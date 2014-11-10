# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

import atexit
import errno
import socket
import subprocess
import time

from twitter.common import log
from twitter.common.quantity import Amount, Time

__all__ = (
  'TunnelHelper',
)


def safe_kill(po):
  """
    Given a Popen object, safely kill it without an unexpected exception.
  """
  try:
    po.kill()
  except OSError as e:
    if e.errno != errno.ESRCH:
      raise
  po.wait()


# TODO(wickman) Add mox tests for this.
class TunnelHelper(object):
  """ Class to initiate an SSH or SOCKS tunnel to a remote host through a tunnel host.

  The ssh binary must be on the PATH.
  """
  TUNNELS = {}
  PROXIES = {}
  MIN_RETRY = Amount(5, Time.MILLISECONDS)
  MAX_INTERVAL = Amount(1, Time.SECONDS)
  WARN_THRESHOLD = Amount(10, Time.SECONDS)
  DEFAULT_TIMEOUT = Amount(5, Time.MINUTES)

  class TunnelError(Exception): pass

  @classmethod
  def log(cls, msg):
    log.debug('%s: %s' % (cls.__name__, msg))

  @classmethod
  def get_random_port(cls):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('localhost', 0))
    _, port = s.getsockname()
    s.close()
    return port

  @classmethod
  def acquire_host_pair(cls, host, port=None):
    port = port or cls.get_random_port()
    return host, port

  @classmethod
  def wait_for_accept(cls, port, tunnel_popen, timeout):
    total_time = Amount(0, Time.SECONDS)
    sleep = cls.MIN_RETRY
    warned = False  # Did we log a warning that shows we're waiting for the tunnel?

    while total_time < timeout and tunnel_popen.returncode is None:
      try:
        accepted_socket = socket.create_connection(('localhost', port), timeout=5.0)
        accepted_socket.close()
        return True
      except socket.error:
        total_time += sleep
        time.sleep(sleep.as_(Time.SECONDS))

        # Increase sleep exponentially until MAX_INTERVAL is reached
        sleep = min(sleep * 2, cls.MAX_INTERVAL)

        if total_time > cls.WARN_THRESHOLD and not warned:
          log.warn('Still waiting for tunnel to be established after %s (timeout is %s)' % (
              total_time, cls.DEFAULT_TIMEOUT))
          warned = True

        tunnel_popen.poll()  # needed to update tunnel_popen.returncode
    if tunnel_popen.returncode is not None:
      cls.log('SSH returned prematurely with code %s' % str(tunnel_popen.returncode))
    else:
      cls.log('timed out initializing tunnel')
    return False

  @classmethod
  def create_tunnel(
      cls,
      remote_host,
      remote_port,
      tunnel_host,
      tunnel_port=None,
      timeout=DEFAULT_TIMEOUT):

    """
      Create or retrieve a memoized SSH tunnel to the remote host & port, using
      tunnel_host:tunnel_port as the tunneling server.
    """
    tunnel_key = (remote_host, remote_port)
    if tunnel_key in cls.TUNNELS:
      return 'localhost', cls.TUNNELS[tunnel_key][0]
    tunnel_host, tunnel_port = cls.acquire_host_pair(tunnel_host, tunnel_port)
    cls.log('opening connection to %s:%s via %s:%s' %
        (remote_host, remote_port, tunnel_host, tunnel_port))
    ssh_cmd_args = ('ssh', '-q', '-N', '-T', '-L',
        '%d:%s:%s' % (tunnel_port, remote_host, remote_port), tunnel_host)
    ssh_popen = subprocess.Popen(ssh_cmd_args, stdin=subprocess.PIPE)
    cls.TUNNELS[tunnel_key] = tunnel_port, ssh_popen
    if not cls.wait_for_accept(tunnel_port, ssh_popen, timeout):
      raise cls.TunnelError('Could not establish tunnel to %s via %s' % (remote_host, tunnel_host))
    cls.log('session established')
    atexit.register(safe_kill, ssh_popen)
    return 'localhost', tunnel_port

  @classmethod
  def create_proxy(cls, proxy_host, proxy_port=None, timeout=DEFAULT_TIMEOUT):
    """
      Create or retrieve a memoized SOCKS proxy using the specified proxy host:port
    """
    if proxy_host in cls.PROXIES:
      return 'localhost', cls.PROXIES[proxy_host][0]
    proxy_host, proxy_port = cls.acquire_host_pair(proxy_host, proxy_port)
    cls.log('opening SOCKS proxy connection through %s:%s' % (proxy_host, proxy_port))
    ssh_cmd_args = ('ssh', '-q', '-N', '-T', '-D', str(proxy_port), proxy_host)
    ssh_popen = subprocess.Popen(ssh_cmd_args, stdin=subprocess.PIPE)
    cls.PROXIES[proxy_host] = (proxy_port, ssh_popen)
    if not cls.wait_for_accept(proxy_port, ssh_popen, timeout):
      raise cls.TunnelError('Could not establish proxy via %s' % proxy_host)
    cls.log('session established')
    atexit.register(safe_kill, ssh_popen)
    return 'localhost', proxy_port

  @classmethod
  def cancel_tunnel(cls, remote_host, remote_port):
    """
      Cancel the SSH tunnel to (remote_host, remote_port) if it exists.
    """
    _, po = cls.TUNNELS.pop((remote_host, remote_port), (None, None))
    if po:
      safe_kill(po)
