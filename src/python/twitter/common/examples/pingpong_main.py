import time

from twitter.common import app, log
from twitter.common.app.modules.http import RootServer
from twitter.common.log.options import LogOptions
from twitter.common.metrics import RootMetrics

from twitter.common.examples.pingpong import PingPongServer


app.add_option('--target_host', default='localhost',
               help='The target host to send pingpong requests.')
app.add_option('--target_port', default=12345, type='int',
               help='The target port to send pingpong requests.')


app.configure('twitter.common.app.modules.http', enable=True)


def main(args, options):
  pingpong = PingPongServer(options.target_host, options.target_port)
  RootServer().mount_routes(pingpong)
  RootMetrics().register_observable('pingpong', pingpong)

  try:
    time.sleep(2**20)
  except KeyboardInterrupt:
    log.info('Shutting down.')


LogOptions.set_disk_log_level('NONE')
LogOptions.set_stderr_log_level('google:DEBUG')
app.main()
