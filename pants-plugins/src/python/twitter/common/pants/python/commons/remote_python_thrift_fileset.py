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

import atexit
import os
import re
import sys
import urllib2

from pants.base.build_environment import get_buildroot

from twitter.common.dirutil import Fileset, safe_delete


# TODO(John Sirois): replace this source fetching backdoor with a proper remote fileset once
# pants supports that (on the pants roadmap): https://github.com/twitter/commons/issues/338
class RemotePythonThriftFileset(object):
  # TODO(wickman) Use the antlr thrift parser to just walk the thrift AST
  # and replace keywords named by 'from' with 'from_'.
  FROM_RE = re.compile('from[,;]*$', re.MULTILINE)
  FROM_REPLACEMENT = 'from_'

  @classmethod
  def factory(cls, parse_context):
    staging_dir = os.path.join(get_buildroot(), parse_context.rel_path)
    return cls(staging_dir)

  def __init__(self, staging_dir):
    self._staging_dir = staging_dir
    self._fetched = []

  def _fetch(self, base_url, sources):
    for source in sources:
      if isinstance(source, tuple):
        assert len(source) == 2, 'Expected source, namespace tuple, got %s' % repr(source)
        source_file, namespace = source
      elif isinstance(source, str):
        source_file, namespace = source, None
      fetch_path = base_url + '/' + source_file
      print('Fetching %s' % fetch_path, file=sys.stderr)
      target_file = os.path.join(self._staging_dir, source_file)
      url = urllib2.urlopen(fetch_path)
      with open(target_file, 'wb') as fp:
        fp.write(self.prefilter(url.read(), namespace=namespace))
      self._fetched.append(target_file)
      yield source_file

  def prefilter(self, content, namespace=None):
    return ''.join(['namespace py %s\n' % namespace if namespace else '',
                    re.sub(self.FROM_RE, self.FROM_REPLACEMENT, content)])

  def cleanup(self):
    for fetched in self._fetched:
      safe_delete(fetched)

  def __call__(self, base_url, sources):
    def fetch():
      atexit.register(self.cleanup)
      return self._fetch(base_url, sources)
    return Fileset(fetch)