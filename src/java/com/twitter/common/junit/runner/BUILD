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

java_library(name='runner',
  provides=artifact(
    org='com.twitter.common',
    name='junit-runner',
    repo='build-support/commons/ivy:public',
  ),
  dependencies=[
    'src/java/com/twitter/common/junit/annotations',
    'src/java/com/twitter/common/junit/runner/withretry',
    '3rdparty/jvm/args4j',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
  ],
  sources=globs('*.java')
)

jvm_binary(name='main',
  main='com.twitter.common.junit.runner.ConsoleRunner',
  dependencies=[
    ":runner",
  ]
).with_description("""
  A replacement for org.junit.runner.JUnitCore.main that adds:
  + support for running individual test methods using [classname]#[methodname]
  + support for stderr and stdout redirection for tests
  + support for ant style junit-report xml output
  + support for per test class timer
  + support for running test classes in parallel
""")
