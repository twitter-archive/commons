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

python_test_suite(name = 'all',
  dependencies = [
    ':recordio',
    ':recordio-thrift',
  ]
)

python_tests(name = 'recordio',
  sources = ['recordio_test.py'],
  dependencies = [
    'src/python/twitter/common/recordio',
    'src/thrift/com/twitter/test:py-thrift',
    '3rdparty/python:mox'
  ],
  coverage = 'twitter.common.recordio'
)

python_tests(name = 'recordio-thrift',
  sources = ['thrift_recordio_test.py'],
  dependencies = [
    'src/python/twitter/common/recordio:recordio-thrift',
    'src/thrift/com/twitter/test:py-thrift'
  ],
  coverage = 'twitter.common.recordio'
)
