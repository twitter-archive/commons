try:
  from twitter.common import log
except ImportError:
  import logging as log

from twitter.common.zookeeper.group import ActiveGroup, Group, GroupInterface

from .endpoint import ServiceInstance


class ServerSet(object):
  """
    A dynamic set of service endpoints tracked by Zookeeper.
  """

  def __init__(self, zk, path, underlying=None, on_join=None, on_leave=None, **kwargs):
    """
      Construct a ServerSet at :path given zookeeper handle :zh.

      If :underlying is specified, use that as the underlying Group implementation.  Must be a
      subclass of twitter.common.zookeeper.group.GroupInterface.

      If :on_join is specified, it will be called with a ServiceInstance object every time a
      new service joins the ServerSet.  If :on_leave is specified, it will be called with
      a ServiceInstance object every time a server leaves the ServerSet.

      All remaining arguments are passed to the underlying Group implementation.
    """
    # The default underlying implementation is Group if no active monitoring is requested
    # of the ServerSet.  If active monitoring is requested by on_join or on_leave, then
    # use ActiveGroup by default, which has better performance on monitor/iter calls.
    default_underlying = Group if (on_join is None and on_leave is None) else ActiveGroup
    underlying = underlying or default_underlying
    assert issubclass(underlying, GroupInterface), (
        'Underlying group implementation must be a subclass of GroupInterface.')
    self._path = path
    self._group = underlying(zk, path, **kwargs)
    def devnull(*args, **kw): pass
    self._on_join = on_join or devnull
    self._on_leave = on_leave or devnull
    self._members = {}
    if on_join or on_leave:
      self._internal_monitor(set(self._members))

  def join(self, endpoint, additional=None, shard=None, callback=None, expire_callback=None):
    """
      Given 'endpoint' (twitter.common.zookeeper.serverset.Endpoint) and an
      optional map 'additional' of string => endpoint (also Endpoint), and an
      optional shard id, create a ServiceInstance and join it into this ServerSet.

      If 'callback' is provided, the join will be done asynchronously and
      'callback' will be called with the Membership object associated with
      the ServiceInstance.  If joining fails, Membership.error() will be
      returned.  If 'callback' is not provided, join will return with this
      information synchronously.

      If 'expire_callback' is provided, it will be called if the membership
      is severed for any reason such as session expiration or malice.
    """
    service_instance = ServiceInstance.pack(ServiceInstance(endpoint, additional, shard=shard))
    return self._group.join(service_instance, callback=callback, expire_callback=expire_callback)

  def cancel(self, membership, callback=None):
    """Cancel membership in the ServerSet."""
    return self._group.cancel(membership, callback=callback)

  def __iter__(self):
    """Iterate over the services (ServiceInstance objects) in this ServerSet."""
    for member in self._group.list():
      try:
        yield ServiceInstance.unpack(self._group.info(member))
      except Exception as e:
        log.warning('Failed to deserialize endpoint: %s' % e)
        continue

  def _internal_monitor(self, members):
    cached = set(self._members)
    new_members = members - cached
    old_members = cached - members
    for service_instance in map(self._members.pop, old_members):
      self._on_leave(service_instance)
    for member_id in new_members:
      def on_finish(service_instance):
        try:
          self._members[member_id] = ServiceInstance.unpack(service_instance)
        except Exception as e:
          log.warning('Failed to deserialize endpoint: %s' % e)
          return
        self._on_join(self._members[member_id])
      self._group.info(member_id, on_finish)
    self._group.monitor(members, self._internal_monitor)
