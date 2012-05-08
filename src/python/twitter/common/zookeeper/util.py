import itertools
import posixpath
import threading
import zookeeper

DEFAULT_ACL = {
  "perms": zookeeper.PERM_ALL,
  "scheme": "world",
  "id": "anyone"
}

ZOO_OPEN_ACL_UNSAFE = DEFAULT_ACL

class ZookeeperUtil:
  @staticmethod
  def host_to_ensemble(host):
    """Given a host or host:port pair, return a zookeeper-compatible ensemble string."""
    import socket

    def convert_ensemble(ensemble):
      ensemble = ensemble.split(':')
      if len(ensemble) == 1:
        ensemble_host, port = ensemble, 2181
      elif len(ensemble) == 2:
        ensemble_host, port = ensemble[0], int(ensemble[1])
      else:
        raise ValueError('Unrecognized ensemble: %s' % ':'.join(ensemble))
      _, _, ensemble = socket.gethostbyname_ex(ensemble_host)
      return ('%s:%s' % (host, port) for host in ensemble)

    return ','.join(itertools.chain(*(convert_ensemble(ensemble) for ensemble in host.split(','))))

  @staticmethod
  def host_to_handle(host, timeout_seconds=5):
    """Given a host or host:port pair, return a zookeeper handle."""
    alive = threading.Event()
    def on_alive(_1, event, state, _2):
      if state == zookeeper.CONNECTED_STATE:
        alive.set()
    zh = zookeeper.init(ZookeeperUtil.host_to_ensemble(host), on_alive)
    alive.wait(timeout=timeout_seconds)
    return zh

  @staticmethod
  def clean_tree(zh, root):
    """Recursively removes the zookeeper subtree underneath :root given zookeeper handle :zh."""
    try:
      if not zookeeper.exists(zh, root):
        return True
      for child in zookeeper.get_children(zh, root):
        if not ZookeeperUtil.clean_tree(zh, posixpath.join(root, child)):
          return False
      zookeeper.delete(zh, root)
    except zookeeper.ZooKeeperException as e:
      return False
    return True

  @staticmethod
  def create_znode(zh, path, acl=[ZOO_OPEN_ACL_UNSAFE]):
    """Safely create a znode for :path, creating all parent znodes if necessary.

    Returns False if path creation fails.  Returns the created path if successful.
    """
    components = path.split('/')
    real_path = path
    path = '/'
    for component in filter(None, components):
      path = posixpath.join(path, component)
      try:
        real_path = zookeeper.create(zh, path, "", acl, 0)
      except zookeeper.NodeExistsException as e:
        continue
      except zookeeper.ZooKeeperException as e:
        return False
    return real_path

  @staticmethod
  def create_parent_znode(zh, path, acl=[ZOO_OPEN_ACL_UNSAFE]):
    """Safely create all the parent znodes necessary to create :path."""
    return ZookeeperUtil.create_znode(zh, posixpath.dirname(path), acl)
