# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  dependencies = [
    ':kazoo_client',
    'src/python/twitter/common/zookeeper/group:kazoo_group',
    'src/python/twitter/common/zookeeper/serverset:kazoo_serverset',

    # keep around zookeeper-old code until we can purge it forever.  do not use
    # these implementations!  use the kazoo implementations!
    ':zookeeper-old',
    'src/python/twitter/common/zookeeper/group',
    'src/python/twitter/common/zookeeper/serverset',
  ],
  provides = setup_py(
    name = 'twitter.common.zookeeper',
    version = commons_version(),
    description = "Implementations of Twitter's service discovery libraries on top of Kazoo.",
    extras_require = {
      # Require twitter.common.zookeeper[old] to pull in zc-zookeeper-static, which will not
      # come by default.  This should be a stopgap until we can remove it completely.
      'old': [
        'zc-zookeeper-static==3.4.4',
      ],
    },
  )
)

python_library(
  name = 'zookeeper-old',
  sources = ['client.py', 'constants.py', 'named_value.py'],
  dependencies = [
    'src/python/twitter/common/log',
    'src/python/twitter/common/metrics',
  ],
)

python_library(
  name = 'kazoo_client',
  sources = ['kazoo_client.py'],
  dependencies = [
    '3rdparty/python:kazoo',
    'src/python/twitter/common/metrics',
  ]
)

python_library(
  name = 'testing',
  sources = TEST_SERVER,
  dependencies = [
    'src/python/twitter/common/contextutil',
    'src/python/twitter/common/dirutil',
    'src/python/twitter/common/rpc/finagle',
    'src/thrift/com/twitter/common/zookeeper/testing/angrybird:py-thrift'
  ]
)
