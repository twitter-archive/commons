from twitter.common import app, log
from twitter.common.app.modules.http import RootServer
from twitter.common.http import HttpServer
from twitter.common.metrics import (
    AtomicGauge,
    LambdaGauge,
    Observable)
from twitter.common.quantity import Amount, Time


class PingPongServer(Observable):
  PING_DELAY = Amount(1, Time.SECONDS)

  def __init__(self):
    self._pings = AtomicGauge('pings')
    self._pongs = AtomicGauge('pongs')
    self.metrics.register(Lamb

  @HttpServer.route('/ping/:message/:ttl')
  def ping(self, message, ttl=60):
    pass
  
  @HttpServer.route('/pong/:message/:ttl')
  def ping(self, message, ttl=60):
    pass


def main(args, options):
  root_server = RootServer()
  root_server.mount_routes(PingPongServer())


app.main()
