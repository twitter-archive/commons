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

from twitter.common.quantity import Amount, Time

try:
  from twitter.common import app
  HAS_APP=True

  app.add_option(
    '--tunnel_host',
    type='string',
    dest='tunnel_host',
    default='nest1.corp.twitter.com',
    help='Host to tunnel commands through (default: %default)')

except ImportError:
  HAS_APP=False


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


class TunnelHelper(object):
  """ Class to initiate an ssh tunnel to a remote host through a tunnel host.

  The ssh binary must be on the PATH.
  """
  TUNNELS = {}
  PROXIES = {}
  MIN_RETRY = Amount(5, Time.MILLISECONDS)

  class TunnelError(Exception): pass

  @classmethod
  def get_random_port(cls):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('localhost', 0))
    _, port = s.getsockname()
    s.close()
    return port

  @staticmethod
  def acquire_host_pair(host=None, port=None):
    if HAS_APP:
      host = host or app.get_options().tunnel_host
    assert host is not None, 'Must specify tunnel host!'
    port = port or TunnelHelper.get_random_port()
    return host, port

  @staticmethod
  def create_tunnel(remote_host, remote_port, tunnel_host=None, tunnel_port=None):
    """
      Create a tunnel via tunnel_host, tunnel_port to the remote_host, remote_port.
      If tunnel_port not supplied, a random port will be chosen.
    """
    tunnel_key = (remote_host, remote_port)
    if tunnel_key in TunnelHelper.TUNNELS:
      return 'localhost', TunnelHelper.TUNNELS[tunnel_key][0]
    tunnel_host, tunnel_port = TunnelHelper.acquire_host_pair(tunnel_host, tunnel_port)

  @classmethod
  def wait_for_accept(cls, port, timeout=Amount(5, Time.SECONDS)):
    total_time = Amount(0, Time.SECONDS)
    timeout = cls.MIN_RETRY
    while total_time < timeout:
      try:
        accepted_socket = socket.create_connection(('localhost', port), timeout=5.0)
        accepted_socket.close()
        return True
      except socket.error:
        total_time += timeout
        time.sleep(timeout.as_(Time.SECONDS))
        timeout *= 2
    return False

  @classmethod
  def create_tunnel(cls, remote_host, remote_port, tunnel_host=None, tunnel_port=None):
    """ Create a tunnel from the localport to the remote host & port,
    using sshd_host as the tunneling server.
    """
    tunnel_key = (remote_host, remote_port)
    if tunnel_key in cls.TUNNELS:
      return 'localhost', cls.TUNNELS[tunnel_key][0]

    if HAS_APP:
      tunnel_host = tunnel_host or app.get_options().tunnel_host
    assert tunnel_host is not None, 'Must specify tunnel host!'
    tunnel_port = tunnel_port or cls.get_random_port()

    ssh_cmd_args = ('ssh', '-T', '-L',
                    '%d:%s:%s' % (tunnel_port,
                                  remote_host,
                                  remote_port),
                    tunnel_host)
    cls.TUNNELS[tunnel_key] = (tunnel_port,
      subprocess.Popen(ssh_cmd_args, stdin=subprocess.PIPE))

    if not cls.wait_for_accept(tunnel_port):
      raise cls.TunnelError('Could not establish tunnel via %s' % remote_host)
    return 'localhost', tunnel_port

  @staticmethod
  def create_proxy(proxy_host=None, proxy_port=None):
    """
      Create a SOCKS proxy.
    """
    if proxy_host in TunnelHelper.PROXIES:
      return 'localhost', TunnelHelper.PROXIES[proxy_host][0]
    proxy_host, proxy_port = TunnelHelper.acquire_host_pair(proxy_host, proxy_port)
    ssh_cmd_args = ('ssh', '-T', '-D', str(proxy_port), proxy_host)
    TunnelHelper.PROXIES[proxy_host] = (proxy_port,
       subprocess.Popen(ssh_cmd_args, stdin=subprocess.PIPE))
    return 'localhost', proxy_port

  @classmethod
  def cancel_tunnel(cls, remote_host, remote_port):
    """
      Cancel the SSH tunnel to (remote_host, remote_port) if it exists.
    """
    _, po = cls.TUNNELS.pop((remote_host, remote_port), (None, None))
    if po:
      safe_kill(po)


@atexit.register
def _cleanup():
  for _, po in TunnelHelper.TUNNELS.values():
    safe_kill(po)
  for _, po in TunnelHelper.PROXIES.values():
    safe_kill(po)
