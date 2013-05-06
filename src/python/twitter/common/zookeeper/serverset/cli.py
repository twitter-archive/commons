from __future__ import print_function

from datetime import datetime
import time

from twitter.common import app
from twitter.common.zookeeper.client import ZooKeeper
from twitter.common.zookeeper.serverset import ServerSet


def main(args):
  if len(args) != 1:
    app.error('Must supply a serverset path to monitor.')

  def on_join(endpoint):
    print('@ %s += %s' % (datetime.now(), endpoint))

  def on_leave(endpoint):
    print('@ %s -= %s' % (datetime.now(), endpoint))

  ss = ServerSet(ZooKeeper(), args[0], on_join=on_join, on_leave=on_leave)

  while True:
    time.sleep(100)


app.main()
