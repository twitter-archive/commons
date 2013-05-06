__all__ = (
  'Endpoint',
  'ServerSet',
  'ServiceInstance',
  'get_serverset_hosts',
)

from .endpoint import Endpoint, ServiceInstance
from .serverset import ServerSet


def get_serverset_hosts(serverset_path, zk):
  ss = ServerSet(zk, serverset_path)
  return list(ss)
