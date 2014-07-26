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


def read_contents_factory(parse_context):
  def read_contents(*paths):
    """Returns the concatenated contents of the files at the given paths relative to this BUILD
    file.
    """
    contents = ''
    for path in paths:
      with open(os.path.join(parse_context.rel_path, path)) as fp:
        contents += fp.read()
    return contents
  return read_contents
