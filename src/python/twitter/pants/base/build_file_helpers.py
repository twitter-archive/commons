# =================================================================================================
# Copyright 2011 Twitter, Inc.
# -------------------------------------------------------------------------------------------------
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
# =================================================================================================


from __future__ import print_function


def maven_layout():
  """Sets up typical maven project source roots for all built-in pants target types."""

  source_root('src/main/antlr')
  source_root('src/main/java')
  source_root('src/main/protobuf')
  source_root('src/main/python')
  source_root('src/main/resources')
  source_root('src/main/scala')
  source_root('src/main/thrift')

  source_root('src/test/java')
  source_root('src/test/python')
  source_root('src/test/resources')
  source_root('src/test/scala')
