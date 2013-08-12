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

java_library(name = 'text',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'text',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    pants('3rdparty:commons-io'),
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:lucene-analyzers-common'),
    pants('3rdparty:lucene-analyzers-smartcn'),
    pants('3rdparty:lucene-core'),
    pants('3rdparty:twitter-text'),
  ],
  excludes = [
    exclude(org = 'javax.jms'),
    exclude(org = 'com.sun.jdmk'),
    exclude(org = 'com.sun.jmx'),
  ],
  sources = rglobs('*.java'),
)
