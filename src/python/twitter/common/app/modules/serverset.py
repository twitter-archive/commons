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

import sys
import threading

from twitter.common import app, options

try:
  from twitter.common import log
except ImportError:
  import logging as log

class ParseError(Exception): pass

def add_port_to(option_name):
  def add_port_callback(option, opt, value, parser):
    if not getattr(parser.values, option_name, None):
      setattr(parser.values, option_name, {})
    try:
      name, port = value.split(':')
    except (ValueError, TypeError):
      raise ParseError('Invalid value for %s: %s should be of form NAME:PORT' % (
        opt, value))
    try:
      port = int(port)
    except ValueError:
      raise ParseError('Port does not appear to be an integer: %s' % port)
    getattr(parser.values, option_name)[name] = port
  return add_port_callback


def set_bool(option, opt_str, value, parser):
  setattr(parser.values, option.dest, not opt_str.startswith('--no'))


class ServerSetModule(app.Module):
  """
    Binds this application to a Zookeeper ServerSet.
  """
  OPTIONS = {
    'serverset-enable': options.Option('--serverset-enable',
        default=False, action='store_true', dest='serverset_module_enable',
        help='Enable the ServerSet module.  Requires --serverset-path and --serverset-primary.'),
    'serverset-ensemble': options.Option('--serverset-ensemble',
        default='zookeeper.local.twitter.com:2181', dest='serverset_module_ensemble',
        metavar='HOST[:PORT]',
        help='The serverset ensemble to talk to.  HOST or HOST:PORT pair.  If the HOST is a RR DNS '
             'record, we fan out to the entire ensemble.  If no port is specified, 2181 assumed.'),
    'serverset-path': options.Option('--serverset-path',
        default=None, dest='serverset_module_path', metavar='PATH', type='str',
        help='The serverset path to join, preferably /twitter/service/(role)/(service)/(env) '
             'where env is prod, staging, devel.'),
    'serverset-primary': options.Option('--serverset-primary',
        type='int', metavar='PORT', dest='serverset_module_primary_port', default=None,
        help='Port on which to bind the primary endpoint.'),
    'serverset-shard-id': options.Option('--serverset-shard-id',
        type='int', metavar='INT', dest='serverset_module_shard_id', default=None,
        help='Shard id to assign this serverset entry.'),
    'serverset-extra': options.Option('--serverset-extra',
        default={}, type='string', nargs=1, action='callback', metavar='NAME:PORT',
        callback=add_port_to('serverset_module_extra'), dest='serverset_module_extra',
        help='Additional endpoints to bind.  Format NAME:PORT.  May be specified multiple times.'),
    'serverset-persistence': options.Option('--serverset-persistence', '--no-serverset-persistence',
        action='callback', callback=set_bool, dest='serverset_module_persistence', default=True,
        help='If serverset persistence is enabled, if the serverset connection is dropped for any '
             'reason, we will retry to connect forever.  If serverset persistence is turned off, '
             'the application will commit seppuku -- sys.exit(1) -- upon session disconnection.'),
  }

  def __init__(self):
    app.Module.__init__(self, __name__, description="ServerSet module")
    self._zookeeper = None
    self._serverset = None
    self._membership = None
    self._join_args = None
    self._torndown = False
    self._rejoin_event = threading.Event()
    self._joiner = None

  @property
  def serverset(self):
    return self._serverset

  @property
  def zh(self):
    if self._zookeeper:
      return self._zookeeper._zh

  def _assert_valid_inputs(self, options):
    if not options.serverset_module_enable:
      return

    assert options.serverset_module_path is not None, (
        'If serverset module enabled, serverset path must be specified.')
    assert options.serverset_module_primary_port is not None, (
        'If serverset module enabled, serverset primary port must be specified.')
    assert isinstance(options.serverset_module_extra, dict), (
        'Serverset additional endpoints must be a dictionary!')
    for name, value in options.serverset_module_extra.items():
      assert isinstance(name, str), 'Additional endpoints must be named by strings!'
      assert isinstance(value, int), 'Additional endpoint ports must be integers!'

    try:
      primary_port = int(options.serverset_module_primary_port)
    except ValueError as e:
      raise ValueError('Could not parse serverset primary port: %s' % e)

  def _construct_serverset(self, options):
    import socket
    import threading
    import zookeeper
    from twitter.common.zookeeper.client import ZooKeeper
    from twitter.common.zookeeper.serverset import Endpoint, ServerSet
    log.debug('ServerSet module constructing serverset.')

    hostname = socket.gethostname()
    primary_port = int(options.serverset_module_primary_port)
    primary = Endpoint(hostname, primary_port)
    additional = dict((port_name, Endpoint(hostname, port_number))
        for port_name, port_number in options.serverset_module_extra.items())

    # TODO(wickman) Add timeout parameterization here.
    self._zookeeper = ZooKeeper(options.serverset_module_ensemble)
    self._serverset = ServerSet(self._zookeeper, options.serverset_module_path)
    self._join_args = (primary, additional)
    self._join_kwargs = ({'shard': options.serverset_module_shard_id}
                         if options.serverset_module_shard_id else {})

  def _join(self):
    log.debug('ServerSet module joining serverset.')
    primary, additional = self._join_args
    self._membership = self._serverset.join(primary, additional, expire_callback=self.on_expiration,
        **self._join_kwargs)

  def on_expiration(self):
    if self._torndown:
      return

    log.debug('Serverset session expired.')
    if not app.get_options().serverset_module_persistence:
      log.debug('Committing seppuku...')
      sys.exit(1)
    else:
      log.debug('Rejoining...')

    self._rejoin_event.set()

  def setup_function(self):
    options = app.get_options()
    if options.serverset_module_enable:
      self._assert_valid_inputs(options)
      self._construct_serverset(options)
      self._thread = ServerSetJoinThread(self._rejoin_event, self._join)
      self._thread.start()
      self._rejoin_event.set()

  def teardown_function(self):
    self._torndown = True
    if self._membership:
      self._serverset.cancel(self._membership)
      self._zookeeper.stop()


class ServerSetJoinThread(threading.Thread):
  """
    A thread to maintain serverset session.
  """
  def __init__(self, event, joiner):
    self._event = event
    self._joiner = joiner
    threading.Thread.__init__(self)
    self.daemon = True

  def run(self):
    while True:
      self._event.wait()
      log.debug('Join event triggered, joining serverset.')
      self._event.clear()
      self._joiner()
