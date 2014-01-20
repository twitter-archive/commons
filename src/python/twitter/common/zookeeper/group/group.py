import functools
import posixpath
import threading
import time

try:
  from twitter.common import log
except ImportError:
  import logging as log

from twitter.common.concurrent import Future
from twitter.common.exceptions import ExceptionalThread
from twitter.common.quantity import Amount, Time
from twitter.common.zookeeper.constants import ReturnCode

from .group_base import (
    Capture,
    GroupBase,
    GroupInterface,
    Membership,
    set_different)

import zookeeper


class Group(GroupBase, GroupInterface):
  """
    An implementation of GroupInterface against CZookeeper.
  """

  def __init__(self, zk, path, acl=None):
    self._zk = zk
    self._path = '/' + '/'.join(filter(None, path.split('/')))  # normalize path
    self._members = {}
    self._member_lock = threading.Lock()
    self._acl = acl or zk.DEFAULT_ACL

  def _prepare_path(self, success):
    class Background(ExceptionalThread):
      BACKOFF = Amount(5, Time.SECONDS)
      def run(_):
        child = '/'
        for component in self._path.split('/')[1:]:
          child = posixpath.join(child, component)
          while True:
            try:
              self._zk.create(child, "", self._acl)
              break
            except zookeeper.NodeExistsException:
              break
            except zookeeper.NoAuthException:
              if self._zk.exists(child):
                break
              else:
                success.set(False)
                return
            except zookeeper.OperationTimeoutException:
              time.sleep(Background.BACKOFF.as_(Time.SECONDS))
              continue
        success.set(True)
    background = Background()
    background.daemon = True
    background.start()

  def info(self, member, callback=None):
    if member == Membership.error():
      raise self.InvalidMemberError('Cannot get info on error member!')

    capture = Capture(callback)

    def do_info():
      self._zk.aget(path, None, info_completion)

    with self._member_lock:
      if member not in self._members:
        self._members[member] = Future()
      member_future = self._members[member]

    member_future.add_done_callback(lambda future: capture.set(future.result()))

    dispatch = False
    with self._member_lock:
      if not member_future.done() and not member_future.running():
        try:
          dispatch = member_future.set_running_or_notify_cancel()
        except:
          pass

    def info_completion(_, rc, content, stat):
      if rc in self._zk.COMPLETION_RETRY:
        do_info()
        return
      if rc == zookeeper.NONODE:
        future = self._members.pop(member, Future())
        future.set_result(Membership.error())
        return
      elif rc != zookeeper.OK:
        return
      self._members[member].set_result(content)

    if dispatch:
      path = posixpath.join(self._path, self.id_to_znode(member.id))
      do_info()

    return capture()

  def join(self, blob, callback=None, expire_callback=None):
    membership_capture = Capture(callback)
    exists_capture = Capture(expire_callback)

    def on_prepared(success):
      if success:
        do_join()
      else:
        membership_capture.set(Membership.error())

    prepare_capture = Capture(on_prepared)

    def do_join():
      self._zk.acreate(posixpath.join(self._path, self.MEMBER_PREFIX),
          blob, self._acl, zookeeper.SEQUENCE | zookeeper.EPHEMERAL, acreate_completion)

    def exists_watch(_, event, state, path):
      if (event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE) or (
          event == zookeeper.DELETED_EVENT):
        exists_capture.set()

    def exists_completion(path, _, rc, stat):
      if rc in self._zk.COMPLETION_RETRY:
        self._zk.aexists(path, exists_watch, functools.partial(exists_completion, path))
        return
      if rc == zookeeper.NONODE:
        exists_capture.set()

    def acreate_completion(_, rc, path):
      if rc in self._zk.COMPLETION_RETRY:
        do_join()
        return
      if rc == zookeeper.OK:
        created_id = self.znode_to_id(path)
        membership = Membership(created_id)
        with self._member_lock:
          result_future = self._members.get(membership, Future())
          result_future.set_result(blob)
          self._members[membership] = result_future
        if expire_callback:
          self._zk.aexists(path, exists_watch, functools.partial(exists_completion, path))
      else:
        membership = Membership.error()
      membership_capture.set(membership)

    self._prepare_path(prepare_capture)
    return membership_capture()

  def cancel(self, member, callback=None):
    capture = Capture(callback)

    def do_cancel():
      self._zk.adelete(posixpath.join(self._path, self.id_to_znode(member.id)),
        -1, adelete_completion)

    def adelete_completion(_, rc):
      if rc in self._zk.COMPLETION_RETRY:
        do_cancel()
        return
      # The rationale here is two situations:
      #   - rc == zookeeper.OK ==> we successfully deleted the znode and the membership is dead.
      #   - rc == zookeeper.NONODE ==> the membership is dead, though we may not have actually
      #      been the ones to cancel, or the node never existed in the first place.  it's possible
      #      we owned the membership but it got severed due to session expiration.
      if rc == zookeeper.OK or rc == zookeeper.NONODE:
        future = self._members.pop(member.id, Future())
        future.set_result(Membership.error())
        capture.set(True)
      else:
        capture.set(False)

    do_cancel()
    return capture()

  def monitor(self, membership=frozenset(), callback=None):
    capture = Capture(callback)

    def wait_exists():
      self._zk.aexists(self._path, exists_watch, exists_completion)

    def exists_watch(_, event, state, path):
      if event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE:
        wait_exists()
        return
      if event == zookeeper.CREATED_EVENT:
        do_monitor()
      elif event == zookeeper.DELETED_EVENT:
        wait_exists()

    def exists_completion(_, rc, stat):
      if rc == zookeeper.OK:
        do_monitor()

    def do_monitor():
      self._zk.aget_children(self._path, get_watch, get_completion)

    def get_watch(_, event, state, path):
      if event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE:
        wait_exists()
        return
      if state != zookeeper.CONNECTED_STATE:
        return
      if event == zookeeper.DELETED_EVENT:
        wait_exists()
        return
      if event != zookeeper.CHILD_EVENT:
        return
      if set_different(capture, membership, self._members):
        return
      do_monitor()

    def get_completion(_, rc, children):
      if rc in self._zk.COMPLETION_RETRY:
        do_monitor()
        return
      if rc == zookeeper.NONODE:
        wait_exists()
        return
      if rc != zookeeper.OK:
        log.warning('Unexpected get_completion return code: %s' % ReturnCode(rc))
        capture.set(set([Membership.error()]))
        return
      self._update_children(children)
      set_different(capture, membership, self._members)

    do_monitor()
    return capture()

  def list(self):
    try:
      return sorted(map(lambda znode: Membership(self.znode_to_id(znode)),
          filter(self.znode_owned, self._zk.get_children(self._path))))
    except zookeeper.NoNodeException:
      return []


class ActiveGroup(Group):
  """
    An implementation of GroupInterface against CZookeeper when iter() and
    monitor() are expected to be called frequently.  Constantly monitors
    group membership and the contents of group blobs.
  """

  def __init__(self, *args, **kwargs):
    super(ActiveGroup, self).__init__(*args, **kwargs)
    self._monitor_queue = []
    self._monitor_members()

  def monitor(self, membership=frozenset(), callback=None):
    capture = Capture(callback)
    if not set_different(capture, membership, self._members):
      self._monitor_queue.append((membership, capture))
    return capture()

  # ---- private api

  def _monitor_members(self):
    def wait_exists():
     self._zk.aexists(self._path, exists_watch, exists_completion)

    def exists_watch(_, event, state, path):
      if event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE:
        wait_exists()
        return
      if event == zookeeper.CREATED_EVENT:
        do_monitor()
      elif event == zookeeper.DELETED_EVENT:
        wait_exists()

    def exists_completion(_, rc, stat):
      if rc == zookeeper.OK:
        do_monitor()

    def do_monitor():
      self._zk.aget_children(self._path, membership_watch, membership_completion)

    def membership_watch(_, event, state, path):
      # Connecting state is caused by transient connection loss, ignore
      if state == zookeeper.CONNECTING_STATE:
        return
      if event == zookeeper.DELETED_EVENT:
        wait_exists()
        return
      # Everything else indicates underlying change.
      do_monitor()

    def membership_completion(_, rc, children):
      if rc in self._zk.COMPLETION_RETRY:
        do_monitor()
        return
      if rc == zookeeper.NONODE:
        wait_exists()
        return
      if rc != zookeeper.OK:
        return

      children = [child for child in children if self.znode_owned(child)]
      _, new = self._update_children(children)
      for child in new:
        def devnull(*args, **kw): pass
        self.info(child, callback=devnull)

      monitor_queue = self._monitor_queue[:]
      self._monitor_queue = []
      members = set(Membership(self.znode_to_id(child)) for child in children)
      for membership, capture in monitor_queue:
        if set(membership) != members:
          capture.set(members)
        else:
          self._monitor_queue.append((membership, capture))

    do_monitor()
