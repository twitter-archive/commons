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


"""A generic thread-safe resource pool."""

try:
  from Queue import Queue
except ImportError:
  from queue import Queue

from twitter.common.quantity import Amount, Time


class Resource(object):
  """Wrapper object around an allocated resource from ResourcePool.

  The underlying resource should generally be only accessed through this
  classes context-manager interface.

  Resources should only be accessed by one thread at a time.
  """

  __slots__ = ('_pool', 'resource')

  def __init__(self, pool, resource):
    self._pool = pool
    self.resource = resource

  def __del__(self):
      try:
        if self._pool is not None:
          self.release()
      except:
        pass

  def release(self):
    """Release the underlying resource back into the pool."""
    self._pool.release(self.resource)
    self._pool = None

  def __enter__(self):
    return self.resource

  def __exit__(self, unused_type, unused_val, unused_tb):
    self.release()

  def __repr__(self):
    return 'Resource(%r)' % self.resource


class ResourcePool(object):
  """A generic resource pool.

    >>> class MyResource(object):
    ...   def __init__(self, name):
    ...     self.name = name
    >>> pool = ResourcePool([MyResource('one'), MyResource('two')])
    >>> with pool.acquire() as resource:
      ...   print resource.name
  """

  def __init__(self, resources):
    """Create a new resource pool populated with resources."""
    self._resources = Queue()
    for resource in resources:
      self._resources.put(resource)

  def acquire(self, timeout=None):
    """Acquire a resource.

    This should generally be only accessed through the context-manager
    interface:

      >>> with pool.acquire() as resource:
      ...   print resource.name

    :param timeout: If provided, seconds (or Amount) to wait for a resource before raising
        Queue.Empty. If not provided, blocks indefinitely.

    :returns: Returns a Resource() wrapper object.
    :raises Empty: No resources are available before timeout.
    """
    if timeout is None:
      resource = self._resources.get()
    else:
      if isinstance(timeout, Amount):
        timeout = timeout.as_(Time.SECONDS)
      resource = self._resources.get(True, timeout)
    return Resource(self, resource)

  def release(self, resource):
    """Add a resource to the pool."""
    self._resources.put(resource)

  def empty(self):
    """Check if any resources are available.

    Note: This is a rough guide only. It does not guarantee that acquire()
        will succeed.
    """
    return self._resources.empty()
