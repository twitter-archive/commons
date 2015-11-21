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
CORE_SOURCES = [
 'Arg.java',
 'ArgParser.java',
 'CmdLine.java',
 'NoParser.java',
 'Parser.java',
 'ParserOracle.java',
 'Positional.java',
 'TestMode.java',
 'Verifier.java',
 'VerifierFor.java',
]
DURATION_PARSER = ['parsers/TimeDurationParser.java']

java_library(name = 'core',
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
  excludes = [
    exclude('com.twitter.common', 'args-core'),
  ],
)

annotation_processor(name = 'apt',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-apt',
    repo = public,
  ),
  dependencies = [
    ':core',
    '3rdparty/jvm/commons-lang:commons-lang',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
  ],
  sources = APT_SOURCES,
  processors = [ 'com.twitter.common.args.apt.CmdLineProcessor' ],
  excludes = [
    exclude('com.twitter.common', 'args-apt'),
  ],
)

java_library(name = 'args',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args',
    repo = public,
  ),
  dependencies = [
    ':apt',
    ':core',
    '3rdparty/jvm/com/google/guava',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
  ],
  sources = rglobs('*.java', exclude=[APT_SOURCES, CORE_SOURCES, DURATION_PARSER]),
  excludes = [
    exclude('com.twitter.common', 'args'),
  ],
  tags = {
    # Prefer TwitterServer and com.twitter.app.Flags for argument parsing.
    'deprecated',
  },
)

java_library(name = 'duration_parser',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'args-duration-parser',
    repo = public,
  ),
  dependencies = [
    ':args',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/twitter:util-core',
  ],
  sources = DURATION_PARSER
)
