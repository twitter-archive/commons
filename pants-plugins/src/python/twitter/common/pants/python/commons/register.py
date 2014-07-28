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

from pants.base.build_file_aliases import BuildFileAliases

from twitter.common.pants.python.commons.read_contents import read_contents_factory
from twitter.common.pants.python.commons.version import Version


def build_file_aliases():
  return BuildFileAliases.create(
      objects=dict(commons_version=Version('src/python/twitter/common/VERSION').version),
      context_aware_object_factories=dict(read_contents=read_contents_factory))
