from functools import partial
import time
import urllib2

from twitter.common import log
from twitter.common.concurrent import defer
from twitter.common.http import HttpServer
from twitter.common.metrics import (
    AtomicGauge,
    LambdaGauge,
    Observable)
from twitter.common.quantity import Amount, Time


class PingPongServer(Observable):
  PING_DELAY = Amount(1, Time.SECONDS)

  def __init__(self, target_host, target_port, clock=time):
    self._clock = clock
    self._target = (target_host, target_port)
    self._pings = AtomicGauge('pings')
    self.metrics.register(self._pings)

  def send_request(self, endpoint, message, ttl):
    url_base = 'http://%s:%d' % self._target
    try:
      urllib2.urlopen('%s/%s/%s/%d' % (url_base, endpoint, message, ttl)).read()
    except Exception as e:
      log.error('Failed to query %s: %s' % (url_base, e))

  @HttpServer.route('/ping/:message')
  @HttpServer.route('/ping/:message/:ttl')
  def ping(self, message, ttl=60):
    self._pings.increment()
    log.info('Got ping (ttl=%s): %s' % (message, ttl))
    ttl = int(ttl) - 1
    if ttl > 0:
      defer(partial(self.send_request, 'ping', message, ttl), delay=self.PING_DELAY,
          clock=self._clock)
