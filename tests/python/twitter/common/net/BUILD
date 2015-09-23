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

python_test_suite(
  name = 'all',
  dependencies = [
    ':socks',
    ':tunnel',
  ]
)

python_tests(
  name = 'socks',
  sources = ['test_socks.py'],
  dependencies = [
    'src/python/twitter/common/net:socks',
    '3rdparty/python:mox'
  ],
)

python_tests(
  name = 'tunnel',
  sources = ['test_tunnel.py'],
  dependencies = [
    'src/python/twitter/common/net:tunnel'
  ],
)
