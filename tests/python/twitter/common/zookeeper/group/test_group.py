import os
import pytest
import threading
import time
import unittest
import zookeeper

from twitter.common.zookeeper.client import ZooKeeper, ZooDefs
from twitter.common.zookeeper.test_server import ZookeeperServer
from twitter.common.zookeeper.group.group import ActiveGroup, Group, Membership


if os.getenv('ZOOKEEPER_TEST_DEBUG'):
  from twitter.common import log
  from twitter.common.log.options import LogOptions
  LogOptions.set_stderr_log_level('DEBUG')
  LogOptions.set_disk_log_level('NONE')
  LogOptions.set_log_dir('/tmp')
  log.init('client_test')


class AlternateGroup(Group):
  MEMBER_PREFIX = 'herpderp_'


class TestGroup(unittest.TestCase):
  GroupImpl = Group
  MAX_EVENT_WAIT_SECS = 30.0
  CONNECT_TIMEOUT_SECS = 10.0
  CONNECT_RETRIES = 6

  @classmethod
  def make_zk(cls, ensemble, **kw):
    return ZooKeeper(ensemble,
                     timeout_secs=cls.CONNECT_TIMEOUT_SECS,
                     max_reconnects=cls.CONNECT_RETRIES,
                     **kw)

  def setUp(self):
    self._server = ZookeeperServer()
    self._zk = self.make_zk(self._server.ensemble)

  def tearDown(self):
    self._zk.stop()
    self._server.stop()

  def test_sync_join(self):
    zkg = self.GroupImpl(self._zk, '/test')
    membership = zkg.join('hello world')
    assert isinstance(membership, Membership)
    assert membership != Membership.error()
    assert zkg.info(membership) == 'hello world'

  def test_join_through_expiration(self):
    zkg = self.GroupImpl(self._zk, '/test')
    session_id = self._zk.session_id()
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
    self._server.expire(self._zk.session_id())
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

    session_id = self._zk.session_id()
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
    secure_zk = self.make_zk(self._server.ensemble, authentication=('digest', 'username:password'))

    # auth => unauth
    zkg = self.GroupImpl(self._zk, '/test')
    secure_zkg = self.GroupImpl(secure_zk, '/test', acl=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
    membership = zkg.join('hello world')
    assert secure_zkg.info(membership) == 'hello world'
    membership = secure_zkg.join('secure hello world')
    assert zkg.info(membership) == 'secure hello world'

    # unauth => auth
    zkg = self.GroupImpl(self._zk, '/secure-test')
    secure_zkg = self.GroupImpl(secure_zk, '/secure-test', acl=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
    membership = secure_zkg.join('hello world')
    assert zkg.info(membership) == 'hello world'
    assert zkg.join('unsecure hello world') == Membership.error()

    # unauth => auth monitor
    zkg = self.GroupImpl(self._zk, '/secure-test2')
    secure_zkg = self.GroupImpl(secure_zk, '/secure-test2', acl=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
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
    self._server.expire(self._zk.session_id())
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

  def test_children_filtering(self):
    zk = self.make_zk(self._server.ensemble)
    zk.create('/test', '', ZooDefs.Acls.OPEN_ACL_UNSAFE)
    zk.create('/test/alt_member_', '',  ZooDefs.Acls.OPEN_ACL_UNSAFE,
        zookeeper.SEQUENCE | zookeeper.EPHEMERAL)
    zk.create('/test/candidate_', '',  ZooDefs.Acls.OPEN_ACL_UNSAFE,
        zookeeper.SEQUENCE | zookeeper.EPHEMERAL)
    zkg = self.GroupImpl(self._zk, '/test')
    assert list(zkg) == []
    assert zkg.monitor(membership=set(['frank', 'larry'])) == set()

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

  def test_monitor_through_expiration(self):
    session_expired = threading.Event()
    def on_watch(_, event, state, path):
      if event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE:
        session_expired.set()

    zk1 = self.make_zk(self._server.ensemble, watch=on_watch)
    zkg1 = self.GroupImpl(self._zk, '/test')
    session_id1 = self._zk.session_id()

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

  def test_against_alternate_groups(self):
    zkg1 = self.GroupImpl(self._zk, '/test')
    zkg2 = AlternateGroup(self._zk, '/test')
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
    secure_zk.create('/test', '', ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
    secure_zk.set_acl('/', 0, ZooDefs.Acls.READ_ACL_UNSAFE)
    secure_zkg = self.GroupImpl(secure_zk, '/test', acl=ZooDefs.Acls.EVERYONE_READ_CREATOR_ALL)
    membership = secure_zkg.join('secure hello world')
    assert membership != Membership.error()
    assert secure_zkg.info(membership) == 'secure hello world'


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
