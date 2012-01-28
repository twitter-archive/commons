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

import atexit
import errno
import os
import signal
import subprocess
import sys
import tempfile

from port_allocator import EphemeralPortAllocator


__author__ = 'Brian Wickman <wickman@twitter.com>'


class ZookeeperServerConfig(object):
  def __init__(self, config, stderr, stdout, port, handle=None):
    self.config = config
    self.stderr = stderr
    self.stdout = stdout
    self.port = port
    self.handle = handle


class ZookeeperClusterBootstrapper(object):
  """Manage local temporary instances of ZK for testing.

  Can also be used as a context manager, in which case it will start the first
  ZK server in the cluster and returns its port.
  """

  class Error(Exception): pass
  class InvalidServerId(Error): pass
  class NotStarted(Error): pass

  ZOOKEEPER_CONFIG = """
dataDir=%(data_dir)s
clientPort=%(server_port)s
initLimit=5
syncLimit=2
"""

  ZOOKEEPER_SERVER_LINE = \
    "server.%(server_number)d=localhost:%(incoming_election_port)s:%(outgoing_election_port)s"

  COMMAND = """
    java -jar %(root)s/build-support/ivy/lib/ivy-2.2.0.jar \
         -settings %(root)s/build-support/ivy/ivysettings.xml \
         -ivy %(ivyfile)s \
         -main org.apache.zookeeper.server.quorum.QuorumPeerMain \
         -args %(config)s
  """

  ROOT = os.path.realpath(os.path.join(os.path.dirname(__file__), "../../../../.."))

  IVY = """
      <ivy-module version="2.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:noNamespaceSchemaLocation="http://ant.apache.org/ivy/schemas/ivy.xsd"
                xmlns:m="http://ant.apache.org/ivy/maven">

      <info organisation="com.twitter.zookeeper" module="test"/>

     <configurations defaultconfmapping="default->default(compile),runtime();sources->sources;docs->@,javadoc">
        <conf name="default" description="normal build"/>
        <conf name="sources" description="java sources"/>
        <conf name="docs" description="javadocs"/>
        <conf name="changelog" description="changelog for artifact pushes"/>
        <conf name="test" visibility="private" description="build and run tests"/>
      </configurations>

      <publications>
        <artifact conf="default" type="jar" ext="jar"/>
        <artifact conf="default" type="pom" ext="pom"/>
        <artifact conf="sources" type="source" m:classifier="sources" ext="jar"/>
        <artifact conf="docs" type="doc" m:classifier="javadoc" ext="jar"/>
        <artifact conf="changelog" type="CHANGELOG" m:classifier="CHANGELOG" ext="txt"/>
      </publications>

      <dependencies>
        <dependency org="org.apache.zookeeper"
                    name="zookeeper"
                    rev="3.3.3"
                    conf="default;sources">
          <exclude org="javax.jms"/>
          <exclude org="com.sun.jdmk"/>
          <exclude org="com.sun.jmx"/>
        </dependency>
      </dependencies>
    </ivy-module>
  """

  _orphaned_pids = set()

  def __init__(self, num_servers=1):
    self.num_servers = num_servers
    self.servers = {}
    self.handles = {}
    self.ivyfile = tempfile.mktemp()
    with open(self.ivyfile, 'w') as ivyfile:
      ivyfile.write(ZookeeperClusterBootstrapper.IVY)
    self._generate_configs()

  def get_init_line(self):
    zookeepers = []
    for server in self.servers.values():
      zookeepers.append('localhost:%s' % server.port)
    return ','.join(zookeepers)

  # return enough ports to satisfy generating the configs
  @staticmethod
  def _generate_ports(num_servers):
    port_allocator = EphemeralPortAllocator()
    ports = {}

    for server_id in range(1, num_servers + 1):
      for port_id in range(3):
        server_port_id = (server_id, port_id)
        ports[server_port_id] = port_allocator.allocate_port(server_port_id)
    return ports

  @staticmethod
  def _bootstrap_tempdir(server_id):
    tempdir = tempfile.mkdtemp()
    with open(os.path.join(tempdir, 'myid'), 'w') as myid_file:
      myid_file.write('%s' % server_id)
    return tempdir

  def _generate_configs(self):
    SERVER_PORT, INCOMING_ELECTION_PORT, OUTGOING_ELECTION_PORT = range(3)

    ports = self._generate_ports(self.num_servers)

    # create the server list for a collection of servers (N/A for a single server)
    server_clause = []
    for server_id in range(1, self.num_servers + 1):
      server_clause.append(ZookeeperClusterBootstrapper.ZOOKEEPER_SERVER_LINE % {
        'server_number': server_id,
        'incoming_election_port': ports[(server_id, INCOMING_ELECTION_PORT)],
        'outgoing_election_port': ports[(server_id, OUTGOING_ELECTION_PORT)]
      })
    server_clause = '\n'.join(server_clause)

    for server_id in range(1, self.num_servers + 1):
      data_dir = self._bootstrap_tempdir(server_id)
      server_port = ports[(server_id, SERVER_PORT)]
      config = ZookeeperClusterBootstrapper.ZOOKEEPER_CONFIG % {
        'data_dir': data_dir,
        'server_port': server_port,
      }
      config = '\n'.join([config, server_clause])
      config_filename = tempfile.mktemp()
      stdout_filename = tempfile.mktemp()
      stderr_filename = tempfile.mktemp()
      with open(config_filename, 'w') as config_file:
        config_file.write(config)
      self.servers[server_id] = ZookeeperServerConfig(
        config_filename,
        open(stderr_filename, 'w'),
        open(stdout_filename, 'w'),
        server_port)

  def start(self, server_id):
    """Start a new (or reuse existing) ZK server.

    :returns: Port the server is listening on.
    """
    if server_id not in self.servers:
      raise ZookeeperClusterBootstrapper.InvalidServerId('Invalid server id: %s' % server_id)
    sh = self.servers[server_id]
    if sh.handle is not None:
      return sh.port
    sh.handle = subprocess.Popen((ZookeeperClusterBootstrapper.COMMAND % {
          'root': ZookeeperClusterBootstrapper.ROOT,
          'ivyfile': self.ivyfile,
          'config': self.servers[server_id].config}).split(),
      stderr=sh.stderr,
      stdout=sh.stdout)
    ZookeeperClusterBootstrapper._orphaned_pids.add(sh.handle.pid)
    return sh.port

  def stop(self, server_id):
    if server_id not in self.servers or self.servers[server_id].handle is None:
      raise ZookeeperClusterBootstrapper.NotStarted(
          'Server has not been started: server_id = %s', server_id)
    server = self.servers[server_id]
    server.handle.kill()
    ZookeeperClusterBootstrapper._orphaned_pids.remove(server.handle.pid)
    server.handle = None

  def __enter__(self):
    return self.start(1)

  def __exit__(self, exc_type, exc_value, traceback):
    self.stop(1)


@atexit.register
def _cleanup_orphans():
  for pid in ZookeeperClusterBootstrapper._orphaned_pids:
    try:
      os.kill(pid, signal.SIGKILL)
    except OSError as e:
      if e.errno != errno.ESRCH:
        print >> sys.stderr, 'warning: error killing orphaned ZK server %d: %s' % (pid, e)
