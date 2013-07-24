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

import os
import threading
import time

from twitter.common.zookeeper.test_server import ZookeeperServer
from twitter.common.zookeeper.group.group_base import Membership

import pytest


class GroupTestBase(object):
  # REQUIRE
  #
  # GroupImpl, AlternateGroupImpl, ACLS

  MAX_EVENT_WAIT_SECS = 30.0
  CONNECT_TIMEOUT_SECS = 10.0
  CONNECT_RETRIES = 6
  SERVER = None
  CHROOT_PREFIX = 0

  @classmethod
  def make_zk(cls, ensemble, **kw):
    raise NotImplementedError

  @classmethod
  def session_id(cls, zk):
    raise NotImplementedError
  
  def setUp(self):
    if GroupTestBase.SERVER is None:
      GroupTestBase.SERVER = ZookeeperServer()
    self._server = GroupTestBase.SERVER
    self._server.restart()
    self._zk = self.make_zk(self._server.ensemble)

  def tearDown(self):
    self._zk.stop()
    GroupTestBase.CHROOT_PREFIX += 1

  def test_sync_join(self):
    zkg = self.GroupImpl(self._zk, '/test')
    membership = zkg.join('hello world')
    assert isinstance(membership, Membership)
    assert membership != Membership.error()
    assert zkg.info(membership) == 'hello world'

  def test_join_through_expiration(self):
    self._zk.live.wait()
    session_id = self.session_id(self._zk)
    zkg = self.GroupImpl(self._zk, '/test')
    self._server.shutdown()
    join_event = threading.Event()
    join_membership = []
    def on_join(membership):
      join_membership[:] = [membership]
      join_event.set()
    cancel_event = threading.Event()
    cancel_membership = []
    def on_cancel():
      cancel_event.set()
    zkg.join('hello world', on_join, on_cancel)
    self._server.expire(session_id)
    self._server.start()
    join_event.wait(self.MAX_EVENT_WAIT_SECS)
    assert join_event.is_set()
    assert not cancel_event.is_set()
    assert zkg.info(join_membership[0]) == 'hello world'
    self._server.expire(self.session_id(self._zk))
    cancel_event.wait(self.MAX_EVENT_WAIT_SECS)
    assert cancel_event.is_set()

  def test_sync_cancel(self):
    # N.B. This test can be nondeterministic.  It is possible for the cancellation
    # to be called prior to zkg.monitor being called, in which case zkg.monitor
    # will return immediately.  If the Python thread scheduler happens to call
    # zkg.monitor first, then an aget_children with watch + completion will be
    # be dispatched and the intended code path will execute.  In both cases,
    # the test should succeed.  This is why we have artificial sleeps, in order
    # to attempt to exercise both cases, but it is not guaranteed.
    zkg = self.GroupImpl(self._zk, '/test')
    class BackgroundMonitor(threading.Thread):
      def __init__(self, *memberships):
        self.memberships = set(memberships)
        self.new_memberships = None
        super(BackgroundMonitor, self).__init__()
      def run(self):
        self.new_memberships = zkg.monitor(membership=self.memberships)

    # immediate
    membership = zkg.join('hello world')
    bm = BackgroundMonitor(membership)
    assert zkg.cancel(membership)
    bm.start()
    bm.join()
    assert bm.new_memberships == set()

    # potentially not immediate
    membership = zkg.join('hello world')
    bm = BackgroundMonitor(membership)
    bm.start()
    time.sleep(0.1)  # > 100ms sleep guaranteed thread yield
    assert zkg.cancel(membership)
    bm.join()
    assert bm.new_memberships == set()

    # multiple
    membership1 = zkg.join('hello world')
    membership2 = zkg.join('herpes derpes')
    bm = BackgroundMonitor(membership1, membership2)
    bm.start()
    assert zkg.cancel(membership1)
    bm.join()
    assert bm.new_memberships == set([membership2])

  def test_cancel_through_expiration(self):
    zkg = self.GroupImpl(self._zk, '/test')
    membership = zkg.join('hello world')

    session_id = self.session_id(self._zk)
    self._server.shutdown()

    cancel_event = threading.Event()
    cancel = []
    def on_cancel(success):
      cancel[:] = [success]
      cancel_event.set()

    zkg.cancel(membership, on_cancel)

    # expire session & restart server
    self._server.expire(session_id)
    self._server.start()

    cancel_event.wait()
    assert cancel_event.is_set()
    assert cancel == [True]

    # TODO(wickman) test a case where on_cancel is provided with false:
    # pretty much the only situation in which this can occur is if the
    # membership is created with a particular ACL and the cancelling Group
    # does not provide one.

  def test_info(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')
    membership = zkg1.join('hello world')
    assert zkg2.info(membership) == 'hello world'

  def test_authentication(self):
    secure_zk = self.make_zk(self._server.ensemble,
        authentication=('digest', 'username:password'))

    # auth => unauth
    zkg = self.GroupImpl(self._zk, '/test')
    secure_zkg = self.GroupImpl(secure_zk, '/test', acl=self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    membership = zkg.join('hello world')
    assert secure_zkg.info(membership) == 'hello world'
    membership = secure_zkg.join('secure hello world')
    assert zkg.info(membership) == 'secure hello world'

    # unauth => auth
    zkg = self.GroupImpl(self._zk, '/secure-test')
    secure_zkg = self.GroupImpl(secure_zk, '/secure-test',
        acl=self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    membership = secure_zkg.join('hello world')
    assert zkg.info(membership) == 'hello world'
    assert zkg.join('unsecure hello world') == Membership.error()

    # unauth => auth monitor
    zkg = self.GroupImpl(self._zk, '/secure-test2')
    secure_zkg = self.GroupImpl(secure_zk, '/secure-test2',
         acl=self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    membership_event = threading.Event()
    members = set()
    def new_membership(m):
      members.update(m)
      membership_event.set()
    zkg.monitor(callback=new_membership)
    membership = secure_zkg.join('hello world')
    membership_event.wait(timeout=1.0)
    assert membership_event.is_set()
    assert members == set([membership])

  def test_monitor_through_parent_death(self):
    zkg = self.GroupImpl(self._zk, '/test')

    membership_event = threading.Event()
    members = set()
    def new_membership(m):
      members.update(m)
      membership_event.set()
    zkg.monitor(callback=new_membership)

    membership = zkg.join('hello world')
    assert membership != Membership.error()

    membership_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert membership_event.is_set()
    assert members == set([membership])

    membership_event.clear()
    members.clear()
    zkg.monitor(set([membership]), callback=new_membership)
    zkg.cancel(membership)

    membership_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert membership_event.is_set()
    assert members == set()

    membership_event.clear()
    members.clear()
    zkg.monitor(callback=new_membership)

    self._zk.delete('/test')

    membership = zkg.join('hello world 2')
    assert membership != Membership.error()

    membership_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert membership_event.is_set()
    assert members == set([membership])

  def test_info_after_expiration(self):
    zkg = self.GroupImpl(self._zk, '/test')
    membership = zkg.join('hello world')
    assert zkg.info(membership) == 'hello world'
    membership_event = threading.Event()
    members = [membership]
    def on_membership(new_membership):
      members[:] = new_membership
      membership_event.set()
    zkg.monitor(members, on_membership)
    self._server.expire(self.session_id(self._zk))
    membership_event.wait()
    assert members == []
    assert zkg.info(membership) == Membership.error()
    membership = zkg.join('herp derp')
    assert zkg.info(membership) == 'herp derp'

  def test_sync_join_with_cancel(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')
    cancel_event = threading.Event()
    def on_cancel():
      cancel_event.set()
    membership = zkg1.join('hello world', expire_callback=on_cancel)
    assert zkg2.cancel(membership)
    cancel_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert cancel_event.is_set()

  def test_async_join(self):
    zkg = self.GroupImpl(self._zk, '/test')
    event = threading.Event()
    memberships = []
    def on_join(membership):
      memberships.append(membership)
      event.set()
    zkg.join('hello world', callback=on_join)
    event.wait()
    assert len(memberships) == 1 and memberships[0] != Membership.error()
    zkg.cancel(memberships[0])

  def test_async_join_with_cancel(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')
    event = threading.Event()
    cancel_event = threading.Event()
    memberships = []
    def on_join(membership):
      memberships.append(membership)
      event.set()
    def on_cancel():
      cancel_event.set()

    # sync
    zkg1.join('hello world', callback=on_join, expire_callback=on_cancel)
    event.wait()
    zkg2.cancel(memberships[0])
    cancel_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert cancel_event.is_set()

    # clear
    event.clear()
    cancel_event.clear()
    memberships = []

    # async
    zkg1.join('hello world', callback=on_join, expire_callback=on_cancel)
    event.wait()

    client_cancel = threading.Event()
    successes = []
    def on_client_side_cancel(troof):
      successes.append(troof)
      client_cancel.set()
    zkg2.cancel(memberships[0], callback=on_client_side_cancel)

    client_cancel.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    cancel_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert client_cancel.is_set()
    assert len(successes) == 1 and successes[0] == True
    assert cancel_event.is_set()

  def test_async_monitor(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')

    membership_event = threading.Event()
    members = set()
    def new_membership(m):
      members.update(m)
      membership_event.set()
    zkg1.monitor(callback=new_membership)
    membership = zkg2.join('hello world')
    membership_event.wait(timeout=self.MAX_EVENT_WAIT_SECS)
    assert membership_event.is_set()
    assert members == set([membership])

  def test_monitor_then_info(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.GroupImpl(self._zk, '/test')
    zkg2.join('hello 1')
    zkg2.join('hello 2')
    zkg2.join('hello 3')
    members = zkg1.monitor()
    for member in members:
      assert zkg1.info(member) is not None
      assert zkg1.info(member).startswith('hello')

  def test_against_alternate_groups(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = self.AlternateGroupImpl(self._zk, '/test')
    assert zkg1.list() == []
    assert zkg2.list() == []
    m1 = zkg1.join('morf gorf')
    assert len(zkg1.list()) == 1
    assert len(zkg2.list()) == 0
    m2 = zkg2.join('herp derp')
    assert len(zkg1.list()) == 1
    assert len(zkg2.list()) == 1
    assert zkg1.info(m1) == 'morf gorf'
    assert zkg1.info(m2) == Membership.error()
    assert zkg2.info(m1) == Membership.error()
    assert zkg2.info(m2) == 'herp derp'

  def test_hard_root_acl(self):
    secure_zk = self.make_zk(self._server.ensemble, authentication=('digest', 'username:password'))
    secure_zk.live.wait()
    secure_zk.create('/test', '', self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    secure_zk.set_acl('/', 0, self.ACLS['READ_ACL_UNSAFE'])
    secure_zkg = self.GroupImpl(secure_zk, '/test', acl=self.ACLS['EVERYONE_READ_CREATOR_ALL'])
    membership = secure_zkg.join('secure hello world')
    assert membership != Membership.error()
    assert secure_zkg.info(membership) == 'secure hello world'
