# ==================================================================================================
# Copyright 2011 Twitter, Inc.
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

import posixpath
import threading
import thrift.TSerialization as codec
import zookeeper
from gen.twitter.thrift.endpoint import ttypes as serverset_types
from twitter.common import app, log
from twitter.common.zookeeper import ZooKeeper


app.add_option(
    '--serverset_retries',
    default=5,
    type=int,
    metavar='RETRIES',
    help='Number of retries when ZK connection is lost before giving up',
    )


class ServerSetClient(object):
  """Represents a dynamic set of service endpoints.

  :param endpoint: ZK path to ServerSet endpoint.
  :param zk: twitter.common.zookeeper.ZooKeeper instance.
  :param watcher: Callback triggered when instances in the endpoint change. Signature is
      watcher(endpoint:str, old_services:[ServiceInstance], new_services:[ServiceInstance])
  :param retries: Number of retries when reconnecting before failing.
  """

  class ServerSetMarkedBad(Exception):
    """This ServerSetClient has been marked bad, a new connection should be established."""
  class ReconnectFailed(Exception):
    """Reconnect attempt failed."""

  def __init__(self, endpoint, zk=None, watcher=None, retries=None):
    self._error = None
    self._endpoint = endpoint
    self._watcher = watcher
    self._lock = threading.Lock()
    self._endpoints = set()
    options = app.get_options()
    self._retries = options.serverset_retries if retries is None else retries
    self._zk = zk or ZooKeeper()
    self._update_endpoints()

  def validate(f):
    """An internal method decorator used to validate if the ZK connection has been marked bad."""
    def _wrapper(self, *args, **kwargs):
      if self._zk is None:
        raise ServerSetClient.ServerSetMarkedBad(
            'ServerSetClient for %r has been marked bad: %r' % (self._endpoint, self._error))
      return f(self, *args, **kwargs)
    return _wrapper

  def get_endpoints(self):
    """Get list of endpoints."""
    with self._lock:
      return self._endpoint[:]

  @validate
  def set_watcher(self, watcher):
    """Set watcher callback for this endpoint."""
    self._watcher = watcher

  @validate
  def __len__(self):
    """Number of endpoints in ServerSet."""
    with self._lock:
      return len(self._endpoints)

  @validate
  def __iter__(self):
    """Iterate over registered endpoints.

    :yields: ServiceInstance instances.
    """
    self._update_endpoints()
    with self._lock:
      snapshot = self._endpoints[:]
    return iter(snapshot)

  def _update_endpoints(self):
    """Update endpoints from ZK.

    This function will block until the ZK servers respond or retry limit is hit.

    :raises ReconnectFailed: If reconnection fails.
    """
    try:
      endpoints = []
      endpoint_names = self._zk.get_children(
          self._endpoint, lambda *args: self._update_endpoints())
      endpoint_names.sort()
      for endpoint in endpoint_names:
        data = self._zk.get(posixpath.join(self._endpoint, endpoint))
        service_endpoint = serverset_types.ServiceInstance()
        endpoints.append(codec.deserialize(service_endpoint, data[0]))

      old = set(map(_format_endpoint, self._endpoints))
      new = set(map(_format_endpoint, endpoints))
      log.debug('ServerSet endpoints at %r changed to: %s' % (self._endpoint, ', '.join(new)))
      log.debug('  Added: %s' % ', '.join(new - old))
      log.debug('  Removed: %s' % ', '.join(old - new))

      with self._lock:
        if self._watcher:
          self._watcher(self._endpoint, self._endpoints, endpoints)
        self._endpoints = endpoints
    except zookeeper.ConnectionLossException:
      log.error('Lost connection to ZooKeeper, reestablishing.')
      self._reconnect()
    except zookeeper.ZooKeeperException as e:
      # TODO(alec): We could implement some retry-logic here, but it is simpler
      # for now to push that logic up to the consumer.
      log.error('Error updating endpoints, marking bad: %s' % e)
      self._endpoints = []
      self._error = str(e)
      self._zk = None
      raise

  # Nuke internal decorator
  del validate

  def _reconnect(self):
    """Reconnect to ZK and update endpoints once complete."""
    for _ in range(self._retries):
      try:
        self._zk.reconnect()
        self._update_endpoints()
        break
      except ZooKeeper.ConnectionTimeout:
        log.warning('Connection establishment to %r timed out, retrying.' % self._zk)
    else:
      raise ServerSetClient.ReconnectFailed('Re-establishment of connection to ZK servers failed')


def _format_endpoint(e):
  return '%s:%s' % (e.serviceEndpoint.host, e.serviceEndpoint.port)
