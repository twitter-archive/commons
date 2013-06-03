from abc import abstractmethod
import posixpath
import threading

from twitter.common.concurrent import Future
from twitter.common.lang import Interface


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


# TODO(wickman) The right abstraction here is probably IAsyncResult from Kazoo.
# Kill this in favor of the better abstraction.
class Capture(object):
  """
    A Capture is a mechanism to capture a value to be dispatched via a
    callback or blocked upon.  If Capture is supplied with a callback, the
    callback is called once the value is available, in which case
    Capture.__call__() will return immediately.  If no callback has been
    supplied, Capture.__call__() blocks until a value is available.
  """
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


def set_different(capture, current_members, actual_members):
  current_members = set(current_members)
  actual_members = set(actual_members)
  if current_members != actual_members:
    capture.set(actual_members)
    return True


class GroupBase(object):
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

  def __iter__(self):
    return iter(self._members)

  def __getitem__(self, member):
    return self.info(member)

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
