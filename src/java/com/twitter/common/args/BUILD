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


jvm_binary(
  name = 'bin',
  main = 'com.twitter.common.args.Arg',
  dependencies = [
    ':args',
  ]
)

jvm_app(
  name="app",
  binary=":bin",
  bundles=[]
)

APT_SOURCES = [
  'apt/CmdLineProcessor.java',
  'apt/Configuration.java',
]
CORE_SOURCES = [
  'Arg.java',
  'ArgParser.java',
  'CmdLine.java',
  'NoParser.java',
  'Parser.java',
  'ParserOracle.java',
  'Positional.java',
  'Verifier.java',
  'VerifierFor.java',
]
DURATION_PARSER = ['parsers/TimeDurationParser.java']

annotation_processor(name = 'core',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-core',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
  ],
  sources = CORE_SOURCES,
)

annotation_processor(name = 'apt',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-apt',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/commons-lang',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    ':core',
  ],
  sources = APT_SOURCES,
  processors = [ 'com.twitter.common.args.apt.CmdLineProcessor' ]
)

java_library(name = 'args',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
    ':apt',
    ':core',
  ],
  sources = rglobs('*.java', exclude = [APT_SOURCES, CORE_SOURCES, DURATION_PARSER])
)

java_library(name = 'duration_parser',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-duration-parser',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/twitter:util-core',
    ':args',
  ],
  sources = DURATION_PARSER
)
