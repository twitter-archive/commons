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

from glob import glob

java_tests(name = 'metrics',
  dependencies = [
    '3rdparty:easymock',
    '3rdparty:guava',
    '3rdparty:junit',
    'src/java/com/twitter/common/metrics',
    'src/java/com/twitter/common/testing',
    'src/java/com/twitter/common/util:system-mocks',
    'src/java/com/twitter/common/util:testing',
  ],
  sources = glob('*.java')
)
