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

import os

from pants.base.build_environment import get_buildroot


class Version(object):
  def __init__(self, rel_path):
    self._version = None
    self._rel_path = rel_path

  def version(self):
    if self._version is None:
      with open(os.path.join(get_buildroot(), self._rel_path)) as fp:
        self._version = fp.read().strip()
    return self._version
