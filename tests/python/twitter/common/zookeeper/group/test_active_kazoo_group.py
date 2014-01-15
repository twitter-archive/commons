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

import unittest

from twitter.common.zookeeper.group.kazoo_group import ActiveKazooGroup, Membership
from twitter.common.zookeeper.group.test_kazoo_group import TestKazooGroup

from kazoo.client import KazooClient
from kazoo.exceptions import NoNodeError, SessionExpiredError
from kazoo.protocol.states import EventType, KeeperState

from mock import ANY, Mock


DEFAULT_PATH = '/some/path/to/group'


def _mock_zk(state=KeeperState.CONNECTED):
  mock_zk = Mock(name='zk', spec=KazooClient)
  mock_zk.state = state

  return mock_zk


def _extract_callback(mock):
  return mock.return_value.rawlink.call_args[0][0]


def _happy_path(group, mock_zk, num_members):
  completion_callback = _extract_callback(mock_zk.get_children_async)

  members = []
  for member_id in range(834, 834 + num_members):
    znode_member_id = ActiveKazooGroup.id_to_znode(member_id)
    mock_get_children_result = Mock(name='mock get children async result')
    mock_get_children_result.get = Mock(return_value=[znode_member_id])

    completion_callback(mock_get_children_result)

    mock_zk.get_async.assert_called_with(DEFAULT_PATH + '/' + znode_member_id)

    info_callback = _extract_callback(mock_zk.get_async)

    member_data = 'some data for member %s' % member_id

    mock_get_result = Mock(name='mock get async result')
    mock_get_result.get = Mock(return_value=(member_data, Mock(name='znode stat')))
    info_callback(mock_get_result)

    member = Membership(member_id)
    members.append(member)
    assert group._members[member].result() == member_data

  return (frozenset(members), completion_callback)


def _unhappy_path(mock_zk, side_effect):
  mock_async_result = Mock()
  mock_async_result.get.side_effect = side_effect

  completion_callback = _extract_callback(mock_zk.get_children_async)

  completion_callback(mock_async_result)

class TestActiveKazooGroup(TestKazooGroup, unittest.TestCase):
  GroupImpl = ActiveKazooGroup

  def test_should_watch_group_path_on_init(self):
    mock_zk = _mock_zk()

    mock_async_result = Mock()
    mock_zk.get_children_async.return_value = mock_async_result

    # we're asserting on the side effects of creating the group
    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    mock_zk.get_children_async.assert_called_with(DEFAULT_PATH, ANY)
    mock_async_result.rawlink.assert_called_with(ANY)

  def test_updates_internal_state_when_children_are_added(self):
    mock_zk = _mock_zk()

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    _happy_path(group, mock_zk, 2)

  def test_updates_internal_state_when_children_are_removed(self):
    mock_zk = _mock_zk()

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    members, completion_callback = _happy_path(group, mock_zk, 2)

    member_remaining, member_removed = list(members)
    znode_member_id = ActiveKazooGroup.id_to_znode(member_remaining.id)
    mock_get_children_result = Mock(name='mock get children async result')
    mock_get_children_result.get = Mock(return_value=[znode_member_id])

    completion_callback(mock_get_children_result)

    assert member_removed not in group._members

  def test_notifies_watchers_when_children_are_added(self):
    mock_zk = _mock_zk()

    mock_callback = Mock(name='monitor callback')

    def first_callback(*args, **kwargs):
      group.monitor(frozenset([Membership(1)]), mock_callback)

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)
    group.monitor(callback=first_callback)

    members, _ = _happy_path(group, mock_zk, 1)

    mock_callback.assert_called_with(members)

  def test_notifies_watchers_when_children_are_removed(self):
    mock_zk = _mock_zk()

    mock_callback = Mock(name='monitor callback')

    def devnull(*args, **kwargs): pass

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)
    group.monitor(frozenset([]), mock_callback)

    members, completion_callback = _happy_path(group, mock_zk, 2)

    member_remaining, member_removed = list(members)
    znode_member_id = ActiveKazooGroup.id_to_znode(member_remaining.id)
    mock_get_children_result = Mock(name='mock get children async result')
    mock_get_children_result.get = Mock(return_value=[znode_member_id])

    completion_callback(mock_get_children_result)

    assert member_removed not in group._members

    mock_callback.assert_called_with(set([member_remaining]))

  def test_waits_for_nodes_to_be_created_if_they_dont_exist(self):
    mock_zk = _mock_zk()

    # we're asserting on the side effects of creating the group
    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    _unhappy_path(mock_zk, NoNodeError)

    mock_zk.exists_async.assert_called_with(DEFAULT_PATH, ANY)
    mock_zk.exists_async.return_value.rawlink.assert_called_with(ANY)

  def test_sets_a_state_listener_if_disconnected(self):
    mock_zk = _mock_zk(state=KeeperState.EXPIRED_SESSION)

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    assert len(group._KazooGroup__listener_queue) == 0

    _unhappy_path(mock_zk, SessionExpiredError)

    assert len(group._KazooGroup__listener_queue) == 1
    state, _ = group._KazooGroup__listener_queue[0]
    assert state == KeeperState.CONNECTED

  def test_znode_watch_triggered_for_child_events_causes_reprocess(self):
    mock_zk = _mock_zk()

    # we're asserting on the side effects of creating the group
    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    assert mock_zk.get_children_async.call_count == 1

    _, watch_callback = mock_zk.get_children_async.call_args[0]

    mock_watch_event = Mock()
    mock_watch_event.state = KeeperState.CONNECTED
    mock_watch_event.type = EventType.CHILD

    watch_callback(mock_watch_event)

    assert mock_zk.get_children_async.call_count == 2

  def test_znode_watch_triggered_for_deleted_znode_causes_wait_for_it_to_exist(self):
    mock_zk = _mock_zk()

    # we're asserting on the side effects of creating the group
    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    assert mock_zk.exists_async.call_count == 0

    _, watch_callback = mock_zk.get_children_async.call_args[0]

    mock_watch_event = Mock()
    mock_watch_event.state = KeeperState.CONNECTED
    mock_watch_event.type = EventType.DELETED

    watch_callback(mock_watch_event)

    assert mock_zk.exists_async.call_count == 1

  def test_sets_a_state_listener_if_disconnected_in_exists_completion(self):
    mock_zk = _mock_zk(state=KeeperState.EXPIRED_SESSION)

    group = ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    _unhappy_path(mock_zk, NoNodeError)

    exists_completion = _extract_callback(mock_zk.exists_async)

    mock_async_result = Mock()
    mock_async_result.get.side_effect = SessionExpiredError

    exists_completion(mock_async_result)

    assert len(group._KazooGroup__listener_queue) == 1
    state, _ = group._KazooGroup__listener_queue[0]
    assert state == KeeperState.CONNECTED

  def test_watches_again_if_no_node_raised_in_exists_completion(self):
    mock_zk = _mock_zk(state=KeeperState.EXPIRED_SESSION)

    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    _unhappy_path(mock_zk, NoNodeError)

    assert mock_zk.exists_async.call_count == 1

    exists_completion = _extract_callback(mock_zk.exists_async)

    mock_async_result = Mock()
    mock_async_result.get.side_effect = NoNodeError

    exists_completion(mock_async_result)

    assert mock_zk.exists_async.call_count == 2

  def test_monitors_when_watched_node_is_created(self):
    mock_zk = _mock_zk(state=KeeperState.EXPIRED_SESSION)

    ActiveKazooGroup(mock_zk, DEFAULT_PATH)

    _unhappy_path(mock_zk, NoNodeError)

    assert mock_zk.get_children_async.call_count == 1

    exists_completion = _extract_callback(mock_zk.exists_async)

    mock_async_result = Mock()

    exists_completion(mock_async_result)

    assert mock_zk.get_children_async.call_count == 2