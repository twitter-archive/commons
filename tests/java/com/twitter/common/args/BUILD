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

MEDIUM_TESTS = [
  'ArgScannerTest.java',
  'OptionInfoTest.java',
]

java_tests(name='small',
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/args:apt',
    'src/java/com/twitter/common/args:duration_parser',
  ],
  sources=globs('*.java',
                'parsers/*.java',
                'argfilterstest/*.java',
                'argfilterstest/subpackageA/*.java',
                'argfilterstest/subpackageA/subsubpackage1/*.java',
                'argfilterstest/subpackageB/*.java',
                'argfilterstest/subpackageBwithSuffix/*.java',
                exclude=[MEDIUM_TESTS])
)

java_tests(name='medium',
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
  ],
  sources=MEDIUM_TESTS
)
