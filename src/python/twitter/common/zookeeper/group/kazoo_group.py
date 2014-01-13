from functools import partial
import posixpath
import threading

try:
  from twitter.common import log
except ImportError:
  import logging as log

from twitter.common.concurrent import Future

from .group_base import (
    Capture,
    GroupBase,
    GroupInterface,
    Membership,
    set_different)

from kazoo.client import KazooClient
from kazoo.protocol.states import (
    EventType,
    KazooState,
    KeeperState)

import kazoo.security as ksec
import kazoo.exceptions as ke


class KazooGroup(GroupBase, GroupInterface):
  """
    An implementation of GroupInterface against Kazoo.
  """
  DISCONNECT_EXCEPTIONS = (ke.ConnectionLoss, ke.OperationTimeoutError, ke.SessionExpiredError)

  @classmethod
  def translate_acl(cls, acl):
    if not isinstance(acl, dict) or any(key not in acl for key in ('perms', 'scheme', 'id')):
      raise TypeError('Expected acl to be Acl-like, got %s' % type(acl))
    return ksec.ACL(acl['perms'], ksec.Id(acl['scheme'], acl['id']))

  @classmethod
  def translate_acl_list(cls, acls):
    if acls is None:
      return acls
    try:
      acls = list(acls)
    except (ValueError, TypeError):
      raise TypeError('ACLs should be a list, got %s' % type(acls))
    if all(isinstance(acl, ksec.ACL) for acl in acls):
      return acls
    else:
      return [cls.translate_acl(acl) for acl in acls]

  def __init__(self, zk, path, acl=None):
    if not isinstance(zk, KazooClient):
      raise TypeError('KazooGroup must be initialized with a KazooClient')
    self._zk = zk
    self._path = '/' + '/'.join(filter(None, path.split('/')))  # normalize path
    self._members = {}
    self._member_lock = threading.Lock()
    self._acl = self.translate_acl_list(acl)

  def __on_state(self, callback, keeper_state):
    def listener(state):
      if state == keeper_state:
        callback()
        return True
    return listener

  def __on_connected(self, callback):
    return self.__on_state(callback, KazooState.CONNECTED)

  def __on_expired(self, callback):
    return self.__on_state(callback, KazooState.LOST)

  def info(self, member, callback=None):
    if member == Membership.error():
      raise self.InvalidMemberError('Cannot get info on error member!')

    capture = Capture(callback)

    def do_info():
      self._zk.get_async(path).rawlink(info_completion)

    with self._member_lock:
      member_future = self._members.setdefault(member, Future())

    member_future.add_done_callback(lambda future: capture.set(future.result()))

    dispatch = False
    with self._member_lock:
      if not member_future.done() and not member_future.running():
        try:
          dispatch = member_future.set_running_or_notify_cancel()
        except:
          pass

    def info_completion(result):
      try:
        content, stat = result.get()
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(do_info))
        return
      except ke.NoNodeException:
        future = self._members.pop(member, Future())
        future.set_result(Membership.error())
        return
      except ke.KazooException as e:
        log.warning('Unexpected Kazoo result in info: (%s)%s' % (type(e), e))
        future = self._members.pop(member, Future())
        future.set_result(Membership.error())
        return
      self._members[member].set_result(content)

    if dispatch:
      path = posixpath.join(self._path, self.id_to_znode(member.id))
      do_info()

    return capture()

  def join(self, blob, callback=None, expire_callback=None):
    membership_capture = Capture(callback)
    expiry_capture = Capture(expire_callback)

    def do_join():
      self._zk.create_async(
          path=posixpath.join(self._path, self.MEMBER_PREFIX),
          value=blob,
          acl=self._acl,
          sequence=True,
          ephemeral=True,
          makepath=True
      ).rawlink(acreate_completion)

    def do_exists(path):
      self._zk.exists_async(path, watch=exists_watch).rawlink(partial(exists_completion, path))

    def exists_watch(event):
      if event.type == EventType.DELETED:
        expiry_capture.set()

    def expire_notifier():
      expiry_capture.set()

    def exists_completion(path, result):
      try:
        if result.get() is None:
          expiry_capture.set()
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(partial(do_exists, path)))

    def acreate_completion(result):
      try:
        path = result.get()
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(do_join))
        return
      except ke.KazooException as e:
        log.warning('Unexpected Kazoo result in join: (%s)%s' % (type(e), e))
        membership = Membership.error()
      else:
        created_id = self.znode_to_id(path)
        membership = Membership(created_id)
        with self._member_lock:
          result_future = self._members.get(membership, Future())
          result_future.set_result(blob)
          self._members[membership] = result_future
        if expire_callback:
          self._zk.add_listener(self.__on_expired(expire_notifier))
          do_exists(path)

      membership_capture.set(membership)

    do_join()
    return membership_capture()

  def cancel(self, member, callback=None):
    capture = Capture(callback)

    def do_cancel():
      self._zk.delete_async(posixpath.join(self._path, self.id_to_znode(member.id))).rawlink(
          adelete_completion)

    def adelete_completion(result):
      try:
        success = result.get()
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(do_cancel))
        return
      except ke.NoNodeError:
        success = True
      except ke.KazooException as e:
        log.warning('Unexpected Kazoo result in cancel: (%s)%s' % (type(e), e))
        success = False

      future = self._members.pop(member.id, Future())
      future.set_result(Membership.error())
      capture.set(success)

    do_cancel()
    return capture()

  def monitor(self, membership=frozenset(), callback=None):
    capture = Capture(callback)

    def wait_exists():
      self._zk.exists_async(self._path, exists_watch).rawlink(exists_completion)

    def exists_watch(event):
      if event.state == KeeperState.EXPIRED_SESSION:
        wait_exists()
        return
      if event.type == EventType.CREATED:
        do_monitor()
      elif event.type == EventType.DELETED:
        wait_exists()

    def exists_completion(result):
      if result.get():
        do_monitor()

    def do_monitor():
      self._zk.get_children_async(self._path, get_watch).rawlink(get_completion)

    def get_watch(event):
      if event.state == KeeperState.EXPIRED_SESSION:
        wait_exists()
        return
      if event.state != KeeperState.CONNECTED:
        return
      if event.type == EventType.DELETED:
        wait_exists()
        return
      if event.type != EventType.CHILD:
        return
      if set_different(capture, membership, self._members):
        return
      do_monitor()

    def get_completion(result):
      try:
        children = result.get()
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(do_monitor))
        return
      except ke.NoNodeError:
        wait_exists()
        return
      except ke.KazooException as e:
        log.warning('Unexpected get_completion result: (%s)%s' % (type(e), e))
        capture.set(set([Membership.error()]))
        return
      self._update_children(children)
      set_different(capture, membership, self._members)

    do_monitor()
    return capture()

  def list(self):
    wait_event = threading.Event()
    while True:
      wait_event.clear()
      try:
        try:
          return sorted(Membership(self.znode_to_id(znode))
                        for znode in self._zk.get_children(self._path)
                        if self.znode_owned(znode))
        except ke.NoNodeException:
          return []
      except self.DISCONNECT_EXCEPTIONS:
        self._zk.add_listener(self.__on_connected(lambda: wait_event.set()))
        wait_event.wait()
