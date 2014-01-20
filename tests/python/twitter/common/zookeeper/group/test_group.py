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

import time
import threading
import unittest

from twitter.common.zookeeper.client import ZooKeeper, ZooDefs
from twitter.common.zookeeper.group.group import ActiveGroup, Group
from twitter.common.zookeeper.group.test_base import GroupTestBase

import zookeeper


class AlternateGroup(Group):
  MEMBER_PREFIX = 'herpderp_'


class TestGroup(GroupTestBase, unittest.TestCase):
  GroupImpl = Group
  AlternateGroupImpl = AlternateGroup
  ACLS = dict(
    OPEN_ACL_UNSAFE=ZooDefs.Acls.OPEN_ACL_UNSAFE,
    CREATOR_ALL_ACL=ZooDefs.Acls.CREATOR_ALL_ACL,
    READ_ACL_UNSAFE=ZooDefs.Acls.READ_ACL_UNSAFE,
    EVERYONE_READ_CREATOR_ALL=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL,
  )

  @classmethod
  def make_zk(cls, ensemble, **kw):
    return ZooKeeper(ensemble,
                     timeout_secs=cls.CONNECT_TIMEOUT_SECS,
                     max_reconnects=cls.CONNECT_RETRIES,
                     **kw)

  @classmethod
  def session_id(cls, zk):
    return zk.session_id

  def test_children_filtering(self):
    zk = self.make_zk(self._server.ensemble)
    zk.create('/test', '', self.ACLS['OPEN_ACL_UNSAFE'])
    zk.create('/test/alt_member_', '',  self.ACLS['OPEN_ACL_UNSAFE'],
        zookeeper.SEQUENCE | zookeeper.EPHEMERAL)
    zk.create('/test/candidate_', '',  self.ACLS['OPEN_ACL_UNSAFE'],
        zookeeper.SEQUENCE | zookeeper.EPHEMERAL)
    zkg = self.GroupImpl(self._zk, '/test')
    assert list(zkg) == []
    assert zkg.monitor(membership=set(['frank', 'larry'])) == set()

  def test_monitor_through_expiration(self):
    session_expired = threading.Event()
    def on_watch(_, event, state, path):
      if event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE:
        session_expired.set()

    zk1 = self.make_zk(self._server.ensemble, watch=on_watch)
    zkg1 = self.GroupImpl(self._zk, '/test')
    session_id1 = self.session_id(zk1)

    zk2 = self.make_zk(self._server.ensemble)
    zkg2 = self.GroupImpl(zk2, '/test')
    member1 = zkg2.join('hello 1')
    new_members = zkg1.monitor([]) # wait until the first group acknowledges the join
    assert new_members == set([member1])

    membership_event = threading.Event()
    membership = []
    def on_membership(new_members):
      membership[:] = new_members
      membership_event.set()

    zkg1.monitor([member1], on_membership)
    self._server.expire(session_id1)
    session_expired.wait(self.MAX_EVENT_WAIT_SECS)
    assert not membership_event.is_set()

    member2 = zkg2.join('hello 2')
    membership_event.wait()
    assert membership_event.is_set()
    assert membership == [member1, member2]

    for member in membership:
      assert zkg1.info(member) is not None
      assert zkg1.info(member).startswith('hello')


class TestActiveGroup(TestGroup):
  GroupImpl = ActiveGroup

  # These tests do use time.sleep but mostly to simulate real eventual consistency
  # in the behavior of iter and getitem.  Because we don't have more intimate control
  # over the underlying store (Zookeeper), this will have to do.
  def test_container_idioms(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')

    def devnull(*args, **kw):
      pass

    def cancel_by_value(group, cancel_group, value):
      for member in group:
        if group[member] == value:
          cancel_group.cancel(member, callback=devnull)
          break

    def assert_iter_equals(membership, max_wait=self.MAX_EVENT_WAIT_SECS):
      total_wait = 0.0
      while total_wait < max_wait:
        members = list(zkg1)
        if len(members) == len(membership):
          break
        time.sleep(0.1)
        total_wait += 0.1
      for member in members:
        assert zkg1[member] in membership

    zkg2.join('hello 1', callback=devnull)
    zkg2.join('hello 2', callback=devnull)
    zkg2.join('hello 3', callback=devnull)
    assert_iter_equals(['hello 1', 'hello 2', 'hello 3'])

    cancel_by_value(zkg1, zkg2, 'hello 2')
    assert_iter_equals(['hello 1', 'hello 3'])

    # cancel on same group
    cancel_by_value(zkg1, zkg1, 'hello 3')
    assert_iter_equals(['hello 1'])

    # join on same group
    zkg1.join('hello 4', callback=devnull)
    assert_iter_equals(['hello 1', 'hello 4'])

    # join on same group
    zkg2.join('hello 5', callback=devnull)
    assert_iter_equals(['hello 1', 'hello 4', 'hello 5'])
