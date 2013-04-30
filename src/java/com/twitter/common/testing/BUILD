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
from twitter.pants import artifact, globs, pants, java_library, jvm_binary

java_library(name='testing',
  dependencies=[
    pants('3rdparty:easymock'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:mockito'),
    pants('3rdparty:tl4j'),
  ],
  sources=[
    'EasyMockTest.java',
    'MockitoTest.java'
  ]
)

java_library(name='action',
  dependencies=[
    pants('3rdparty:guava'),
    pants('3rdparty:tl4j'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources=[ 'TearDownRegistry.java' ]
)

java_library(name='junit-runner-annotations',
  provides=artifact(
    org='com.twitter.common',
    name='junit-runner-annotations',
    repo=pants('build-support/commons/ivy:public'),
  ),
  sources=globs('runner/annotations/*.java')
)

java_library(name='junit-runner',
  provides=artifact(
    org='com.twitter.common',
    name='junit-runner',
    repo=pants('build-support/commons/ivy:public'),
  ),
  dependencies=[
    pants(':junit-runner-annotations'),
    pants('3rdparty:args4j'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
  ],
  sources=globs('runner/*.java')
).with_description("""
  A replacement for org.junit.runner.JUnitCore.main that adds:
  + support for running individual test methods using [classname]#[methodname]
  + support for stderr and stdout redirection for tests
  + support for ant style junit-report xml output
  + support for per test class timer
  + support for running test classes in parallel
""")

jvm_binary(name='junit-runner-main',
  main='com.twitter.common.testing.runner.JUnitConsoleRunner',
  dependencies=[
    pants(":junit-runner")
  ]
)
