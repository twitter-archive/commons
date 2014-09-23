# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

java_library(name='tool',
  provides=artifact(
    org='com.twitter.common',
    name='jar-tool',
    repo='build-support/commons/ivy:public',
  ),
  dependencies=[
    # Note both commons-io & twitter/common/io have a FileUtils class we use - 1 imported, 1 fully
    # qualified.
    '3rdparty/jvm/commons-io',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/logging',
  ],
  sources=globs('*.java')
)

jvm_binary(name='main',
  basename='jar-tool',
  main='com.twitter.common.jar.tool.Main',
  dependencies=[
    ":tool",
  ]
).with_description('A jar tool that supports adding duplicate entries.')

