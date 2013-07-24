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
import unittest

from twitter.common.zookeeper.group.group_base import Membership
from twitter.common.zookeeper.group.kazoo_group import KazooGroup
from twitter.common.zookeeper.group.test_base import GroupTestBase
from twitter.common.zookeeper.kazoo_client import TwitterKazooClient

from kazoo.protocol.states import KazooState
import kazoo.security as ksec


class AlternateKazooGroup(KazooGroup):
  MEMBER_PREFIX = 'herpderp_'


class TestKazooGroup(GroupTestBase, unittest.TestCase):
  GroupImpl = KazooGroup
  AlternateGroupImpl = AlternateKazooGroup
  ACLS = dict(
    OPEN_ACL_UNSAFE=ksec.OPEN_ACL_UNSAFE,
    CREATOR_ACLL_ACL=ksec.CREATOR_ALL_ACL,
    READ_ACL_UNSAFE=ksec.READ_ACL_UNSAFE,
    EVERYONE_READ_CREATOR_ALL=[
        dict(scheme='auth', id='', perms=ksec.Permissions.ALL),
        dict(scheme='world', id='anyone', perms=ksec.Permissions.READ),
    ]
  )

  @classmethod
  def make_zk(cls, ensemble, **kw):
    if 'authentication' in kw:
      kw.update(auth_data = [kw.pop('authentication')])
    tzk = TwitterKazooClient.make(
        ensemble + ('/%08d' % GroupTestBase.CHROOT_PREFIX),
        timeout=cls.CONNECT_TIMEOUT_SECS,
        max_retries=cls.CONNECT_RETRIES, **kw)
    started = threading.Event()
    def listen(state):
      if state == KazooState.CONNECTED:
        started.set()
      return True
    tzk.add_listener(listen)
    started.wait()
    tzk.ensure_path('/')
    return tzk

  @classmethod
  def session_id(cls, zk):
    return zk._session_id

  def test_children_filtering(self):
    zk = self.make_zk(self._server.ensemble)
    zk.live.wait()
    zk.create('/test', '', acl=ksec.OPEN_ACL_UNSAFE)
    zk.create('/test/alt_member_', '',  acl=ksec.OPEN_ACL_UNSAFE, sequence=True, ephemeral=True)
    zk.create('/test/candidate_', '',  acl=ksec.OPEN_ACL_UNSAFE, sequence=True, ephemeral=True)
    zkg = self.GroupImpl(self._zk, '/test')
    assert list(zkg) == []
    assert zkg.monitor(membership=set(['frank', 'larry'])) == set()

  def test_hard_root_acl(self):
    secure_zk = self.make_zk(self._server.ensemble,
        authentication=('digest', 'username:password'))
    secure_zk.live.wait()
    secure_zk.create('/test', '', acl=ksec.CREATOR_ALL_ACL + ksec.READ_ACL_UNSAFE)
    secure_zk.set_acls('/', self.ACLS['READ_ACL_UNSAFE'])
    secure_zkg = self.GroupImpl(secure_zk, '/test', acl=self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    membership = secure_zkg.join('secure hello world')
    assert membership != Membership.error()
    assert secure_zkg.info(membership) == 'secure hello world'

  def test_monitor_expire_then_create(self):
    session_expired = threading.Event()
    membership_event = threading.Event()

    def listener(state):
      if state == KazooState.LOST:
        session_expired.set()

    def on_membership(new_members):
      membership_event.set()

    zk1 = self.make_zk(self._server.ensemble)
    zk1.add_listener(listener)
    zk1.live.wait()

    zkg1 = self.GroupImpl(self._zk, '/test')

    zk2 = self.make_zk(self._server.ensemble)
    zkg2 = self.GroupImpl(zk2, '/test')

    zkg1.monitor([], on_membership)

    self._server.expire(self.session_id(zk1))
    session_expired.wait(self.MAX_EVENT_WAIT_SECS)

    member1 = zkg2.join('hello 1')
    membership_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert membership_event.is_set()

  def test_monitor_through_expiration(self):
    session_expired = threading.Event()

    def listener(state):
      if state == KazooState.LOST:
        session_expired.set()

    zk1 = self.make_zk(self._server.ensemble)
    zk1.add_listener(listener)
    zkg1 = self.GroupImpl(self._zk, '/test')

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
    self._server.expire(self.session_id(zk1))
    session_expired.wait(self.MAX_EVENT_WAIT_SECS)
    assert session_expired.is_set()
    assert not membership_event.is_set()

    member2 = zkg2.join('hello 2')
    membership_event.wait()
    assert membership_event.is_set()
    assert membership == [member1, member2]

    for member in membership:
      assert zkg1.info(member) is not None
      assert zkg1.info(member).startswith('hello')
