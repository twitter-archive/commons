# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

python_test_suite(name = 'log',
  dependencies = [
    pants(':test_log'),
    pants(':test_log_with_scribe'),
  ]
)

python_tests(name = 'test_log',
  sources = globs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/lang'),
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/testing'),
    pants('3rdparty/python:mox')
  ],
  coverage = 'twitter.common.log'
)

python_tests(name = 'test_log_with_scribe',
  sources = globs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/lang'),
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/testing'),
    pants('src/thrift/org/apache/scribe:py-scribe'),
    pants('3rdparty/python:mox')
  ],
  coverage = 'twitter.common.log'
)
