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

import errno
import random
import socket
import time

from twitter.common import log


__author__ = 'Brian Wickman <wickman@twitter.com>'


class EphemeralPortAllocator(object):
  """Dynamically allocate and track ports."""

  SOCKET_RANGE = (32768, 65535)

  class PortConflict(Exception): pass

  def __init__(self):
    self._ports = {}

  def allocate_port(self, name, port=None):
    if port is not None:
      if name in self._ports and self._ports[name] != port:
        raise EphemeralPortAllocator.PortConflict(
            'Port binding %s=>%s conflicts with current binding %s=>%s' % (
          name, port, name, self._ports[name]))
      else:
        self._ports[name] = port
        return port

    if name in self._ports:
      return self._ports[name]

    while True:
      rand_port = random.randint(*EphemeralPortAllocator.SOCKET_RANGE)
      # if this ever needs to be performant, make a peer set.
      if rand_port in self._ports.values():
        continue
      try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(('localhost', rand_port))
        s.close()
        self._ports[name] = rand_port
        break
      except OSError as e:
        if e.errno == errno.EADDRINUSE:
          log.warning('Could not bind port: %s' % e)
          time.sleep(0.2)
          continue
        else:
          raise
    return self._ports[name]
