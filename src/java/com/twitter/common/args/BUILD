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

APT_SOURCES = globs('apt/*.java')

annotation_processor(name = 'apt',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-apt',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:commons-lang'),
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
  ],
  sources = APT_SOURCES,
  processors = [ 'com.twitter.common.args.apt.CmdLineProcessor' ]
)

java_library(name = 'args',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants(':apt'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = rglobs('*.java') - APT_SOURCES
)
