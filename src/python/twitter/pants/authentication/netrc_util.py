# ==================================================================================================
# Copyright 2014 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from __future__ import print_function

import collections
import os

from netrc import netrc as NetrcDb, NetrcParseError

from twitter.pants.tasks.task_error import TaskError


class Netrc(object):

  def __init__(self):
    self._login = collections.defaultdict(lambda: None)
    self._password = collections.defaultdict(lambda: None)

  def getusername(self, repository):
    self._ensure_loaded()
    return self._login[repository]

  def getpassword(self, repository):
    self._ensure_loaded()
    return self._password[repository]

  def _ensure_loaded(self):
    if not self._login and not self._password:
      db = os.path.expanduser('~/.netrc')
      if not os.path.exists(db):
        raise TaskError('A ~/.netrc file is required to authenticate')
      try:
        db = NetrcDb(db)
        for host, value in db.hosts.items():
          auth = db.authenticators(host)
          if auth:
            login, _, password = auth
            self._login[host] = login
            self._password[host] = password
        if len(self._login) == 0:
          raise TaskError('Found no usable authentication blocks for twitter in ~/.netrc')
      except NetrcParseError as e:
        raise TaskError('Problem parsing ~/.netrc: %s' % e)
