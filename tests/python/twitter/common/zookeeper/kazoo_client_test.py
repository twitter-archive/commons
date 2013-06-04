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

import threading
import time

from twitter.common.zookeeper.kazoo_client import TwitterKazooClient
from twitter.common.zookeeper.test_server import ZookeeperServer


MAX_EVENT_WAIT_SECS = 30.0


def test_metrics():
  with ZookeeperServer() as server:
    event = threading.Event()
    def state_change(state):
      event.set()
      return True

    zk = TwitterKazooClient('localhost:%d' % server.zookeeper_port)
    zk.start()
    zk.live.wait(timeout=MAX_EVENT_WAIT_SECS)
    zk.add_listener(state_change)
    sample = zk.metrics.sample()
    assert sample['session_id'] == zk._session_id
    assert sample['session_expirations'] == 0
    assert sample['connection_losses'] == 0
    old_session_id = zk._session_id

    server.expire(zk._session_id)
    event.wait(timeout=MAX_EVENT_WAIT_SECS)

    zk.live.wait(timeout=MAX_EVENT_WAIT_SECS)

    sample = zk.metrics.sample()
    assert sample['session_id'] == zk._session_id
    assert old_session_id != zk._session_id
    assert sample['session_expirations'] == 1
    assert sample['connection_losses'] > 0
