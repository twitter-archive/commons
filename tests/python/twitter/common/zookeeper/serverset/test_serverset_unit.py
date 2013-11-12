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

from twitter.common.zookeeper.serverset.endpoint import ServiceInstance
from twitter.common.zookeeper.serverset.serverset import ServerSet
from twitter.common.zookeeper.group.group_base import GroupInterface, Membership

from twitter.common.zookeeper.group.kazoo_group import ActiveKazooGroup

from kazoo.client import KazooClient

import mock


SERVICE_INSTANCE_JSON = '''{
    "additionalEndpoints": {
        "aurora": {
            "host": "smfd-aki-15-sr1.devel.twitter.com",
            "port": 31510
        },
        "health": {
            "host": "smfd-aki-15-sr1.devel.twitter.com",
            "port": 31510
        }
    },
    "serviceEndpoint": {
        "host": "smfd-aki-15-sr1.devel.twitter.com",
        "port": 31510
    },
    "shard": 0,
    "status": "ALIVE"
}'''


@mock.patch('twitter.common.zookeeper.serverset.serverset.ActiveKazooGroup')
@mock.patch('twitter.common.zookeeper.serverset.serverset.validate_group_implementation')
def test_internal_monitor(mock_group_impl_validator, MockActiveKazooGroup):
  mock_zk = mock.Mock(spec=KazooClient)
  mock_group = mock.MagicMock(spec=GroupInterface)
  MockActiveKazooGroup.mock_add_spec(ActiveKazooGroup)
  MockActiveKazooGroup.return_value = mock_group

  # by default it tries to assert that the group impl is a subclass of GroupInterface
  # since the group impl will be a mock, it doesn't pass that check, so we mock the validator
  # as well.
  mock_group_impl_validator.return_value = True

  def devnull(*args, **kwargs): pass

  serverset = ServerSet(
      mock_zk,
      '/some/path/to/group',
      on_join=devnull,
      on_leave=devnull)

  members = [Membership(id) for id in range(2)]

  print("Members are: %s" % members)
  serverset._internal_monitor(frozenset(members))

  for call in mock_group.info.mock_calls:
    _, (_, callback), _ = call
    callback(ServiceInstance.unpack(SERVICE_INSTANCE_JSON))

  assert len(serverset._members) == 2
