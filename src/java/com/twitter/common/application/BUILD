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

ACTION_SOURCES = set([
    'Lifecycle.java',
    'ShutdownRegistry.java',
    'ShutdownStage.java',
    'StartupRegistry.java',
    'StartupStage.java',
])

java_library(name = 'action',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-action',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/commons-lang'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = ACTION_SOURCES
)

java_library(name = 'application',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('src/java/com/twitter/common/application/modules:applauncher'),
    pants('src/java/com/twitter/common/application/modules:lifecycle'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net/http/handlers'),
  ],
  sources = globs('*.java') - ACTION_SOURCES
)
