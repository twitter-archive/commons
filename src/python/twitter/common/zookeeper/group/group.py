from abc import abstractmethod
import functools
import posixpath
import socket
import threading
import zookeeper

try:
  from twitter.common import log
except ImportError:
  import logging as log

from twitter.common.concurrent import Future
from twitter.common.exceptions import ExceptionalThread
from twitter.common.lang import Interface
from twitter.common.zookeeper.constants import ReturnCode


class Membership(object):
  ERROR_ID = -1

  @staticmethod
  def error():
    return Membership(_id=Membership.ERROR_ID)

  def __init__(self, _id):
    self._id = _id

  @property
  def id(self):
    return self._id

  def __lt__(self, other):
    return self._id < other._id

  def __eq__(self, other):
    if not isinstance(other, Membership):
      return False
    return self._id == other._id

  def __ne__(self, other):
    return not self == other

  def __hash__(self):
    return hash(self._id)

  def __repr__(self):
    if self._id == Membership.ERROR_ID:
      return 'Membership.error()'
    else:
      return 'Membership(%r)' % self._id


class GroupInterface(Interface):
  """
    A group of members backed by immutable blob data.
  """

  @abstractmethod
  def join(self, blob, callback=None, expire_callback=None):
    """
      Joins the Group using the blob.  Returns Membership synchronously if
      callback is None, otherwise returned to callback.  Returns
      Membership.error() on failure to join.

      If expire_callback is provided, it is called with no arguments when
      the membership is terminated for any reason.
    """

  @abstractmethod
  def info(self, membership, callback=None):
    """
      Given a membership, return the blob associated with that member or
      Membership.error() if no membership exists.  If callback is provided,
      this operation is done asynchronously.
    """

  @abstractmethod
  def cancel(self, membership, callback=None):
    """
      Cancel a given membership.  Returns true if/when the membership does not
      exist.  Returns false if the membership exists and we failed to cancel
      it.  If callback is provided, this operation is done asynchronously.
    """

  @abstractmethod
  def monitor(self, membership_set=frozenset(), callback=None):
    """
      Given a membership set, return once the underlying group membership is
      different.  If callback is provided, this operation is done
      asynchronously.
    """

  @abstractmethod
  def list(self):
    """
      Synchronously return the list of underlying members.  Should only be
      used in place of monitor if you cannot afford to block indefinitely.
    """


class Promise(object):
  def __init__(self, callback=None):
    self._value = None
    self._event = threading.Event()
    self._callback = callback

  def set(self, value=None):
    self._value = value
    self._event.set()
    if self._callback:
      if value is not None:
        self._callback(value)
      else:
        self._callback()
      self._callback = None

  def get(self):
    self._event.wait()
    return self._value

  def __call__(self):
    if self._callback:
      return None
    return self.get()


def set_different(promise, current_members, actual_members):
  current_members = set(current_members)
  actual_members = set(actual_members)
  if current_members != actual_members:
    promise.set(actual_members)
    return True


class Group(GroupInterface):
  """
    An implementation of GroupInterface against Zookeeper.
  """

  class GroupError(Exception): pass
  class InvalidMemberError(GroupError): pass

  MEMBER_PREFIX = 'member_'

  @classmethod
  def znode_owned(cls, znode):
    return posixpath.basename(znode).startswith(cls.MEMBER_PREFIX)

  @classmethod
  def znode_to_id(cls, znode):
    znode_name = posixpath.basename(znode)
    assert znode_name.startswith(cls.MEMBER_PREFIX)
    return int(znode_name[len(cls.MEMBER_PREFIX):])

  @classmethod
  def id_to_znode(cls, _id):
    return '%s%010d' % (cls.MEMBER_PREFIX, _id)

  def __init__(self, zk, path, acl=None):
    self._zk = zk
    self._path = '/' + '/'.join(filter(None, path.split('/')))  # normalize path
    self._members = {}
    self._member_lock = threading.Lock()
    self._acl = acl or zk.DEFAULT_ACL

  def _prepare_path(self, success):
    class Background(ExceptionalThread):
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
              pass # retry
        success.set(True)
    background = Background()
    background.daemon = True
    background.start()

  def __iter__(self):
    return iter(self._members)

  def __getitem__(self, member):
    return self.info(member)

  def info(self, member, callback=None):
    if member == Membership.error():
      raise self.InvalidMemberError('Cannot get info on error member!')

    promise = Promise(callback)

    def do_info():
      self._zk.aget(path, None, info_completion)

    with self._member_lock:
      if member not in self._members:
        self._members[member] = Future()
      member_future = self._members[member]

    member_future.add_done_callback(lambda future: promise.set(future.result()))

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

    return promise()

  def join(self, blob, callback=None, expire_callback=None):
    membership_promise = Promise(callback)
    exists_promise = Promise(expire_callback)

    def on_prepared(success):
      if success:
        do_join()
      else:
        membership_promise.set(Membership.error())

    prepare_promise = Promise(on_prepared)

    def do_join():
      self._zk.acreate(posixpath.join(self._path, self.MEMBER_PREFIX),
          blob, self._acl, zookeeper.SEQUENCE | zookeeper.EPHEMERAL, acreate_completion)

    def exists_watch(_, event, state, path):
      if (event == zookeeper.SESSION_EVENT and state == zookeeper.EXPIRED_SESSION_STATE) or (
          event == zookeeper.DELETED_EVENT):
        exists_promise.set()

    def exists_completion(path, _, rc, stat):
      if rc in self._zk.COMPLETION_RETRY:
        self._zk.aexists(path, exists_watch, functools.partial(exists_completion, path))
        return
      if rc == zookeeper.NONODE:
        exists_promise.set()

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
      membership_promise.set(membership)

    self._prepare_path(prepare_promise)
    return membership_promise()

  def cancel(self, member, callback=None):
    promise = Promise(callback)

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
        promise.set(True)
      else:
        promise.set(False)

    do_cancel()
    return promise()

  def monitor(self, membership=frozenset(), callback=None):
    promise = Promise(callback)

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
      if set_different(promise, membership, self._members):
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
        promise.set(set([Membership.error()]))
        return
      self._update_children(children)
      set_different(promise, membership, self._members)

    do_monitor()
    return promise()

  def list(self):
    try:
      return sorted(map(lambda znode: Membership(self.znode_to_id(znode)),
          filter(self.znode_owned, self._zk.get_children(self._path))))
    except zookeeper.NoNodeException:
      return []

  # ---- protected api

  def _update_children(self, children):
    """
      Given a new child list [znode strings], return a tuple of sets of Memberships:
        left: the children that left the set
        new: the children that joined the set
    """
    cached_children = set(self._members)
    current_children = set(Membership(self.znode_to_id(znode))
        for znode in filter(self.znode_owned, children))
    new = current_children - cached_children
    left = cached_children - current_children
    for child in left:
      future = self._members.pop(child, Future())
      future.set_result(Membership.error())
    for child in new:
      self._members[child] = Future()
    return left, new


class ActiveGroup(Group):
  """
    An implementation of GroupInterface against Zookeeper when iter() and
    monitor() are expected to be called frequently.  Constantly monitors
    group membership and the contents of group blobs.
  """

  def __init__(self, *args, **kwargs):
    super(ActiveGroup, self).__init__(*args, **kwargs)
    self._monitor_queue = []
    self._monitor_members()

  def monitor(self, membership=frozenset(), callback=None):
    promise = Promise(callback)
    if not set_different(promise, membership, self._members):
      self._monitor_queue.append((membership, promise))
    return promise()

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
      for membership, promise in monitor_queue:
        if set(membership) != members:
          promise.set(members)
        else:
          self._monitor_queue.append((membership, promise))

    do_monitor()
