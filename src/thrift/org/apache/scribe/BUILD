# ==================================================================================================
# Copyright 2011 Twitter, Inc.
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

java_thrift_library(name = 'scribe_client-0.1.2',
  provides = artifact(
    org = 'org.apache.scribe',
    name = 'scribe_client',
    repo = public,
  ),
  sources = globs('0.1.2/*.thrift')
)

java_thrift_library(name = 'scribe_client-0.1.2-scala',
  language='scala',
  rpc_style='finagle',
  compiler='scrooge',
  sources = globs('0.1.2/*.thrift')
)

python_thrift_library(
  name = 'py-scribe',
  sources = globs('0.1.2/*.thrift'),
)

