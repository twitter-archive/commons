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

java_tests(name = 'metrics',
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    '3rdparty/jvm/org/pantsbuild:junit-runner-annotations',
    'src/java/com/twitter/common/metrics',
    'src/java/com/twitter/common/objectsize',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/testing/easymock',
    'src/java/com/twitter/common/util:system-mocks',
    'src/java/com/twitter/common/util:testing',
  ],
  sources = globs('*.java')
)

jvm_binary(name = 'metrics-precision',
  main = 'com.twitter.common.metrics.demo.MetricsPrecisionDemo',
  source = 'demo/MetricsPrecisionDemo.java',
  dependencies = [
    '3rdparty/jvm/com/twitter/common:metrics-data-sample',
    'tests/java/com/twitter/common/metrics',
    'src/java/com/twitter/common/metrics',
    'src/java/com/twitter/common/objectsize',
    'src/java/com/twitter/common/stats/testing:histogram',
  ],
  description="""
Demo code to show precision/memory performance based on constructor
parameters (used to find default values)
  """
)


