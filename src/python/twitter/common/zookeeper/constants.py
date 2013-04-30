import zookeeper

from .named_value import NamedValue


class Event(NamedValue):
  MAP = {
    0: 'UNKNOWN',
    zookeeper.CREATED_EVENT: 'CREATED',
    zookeeper.DELETED_EVENT: 'DELETED',
    zookeeper.CHANGED_EVENT: 'CHANGED',
    zookeeper.CHILD_EVENT: 'CHILD',
    zookeeper.SESSION_EVENT: 'SESSION',
    zookeeper.NOTWATCHING_EVENT: 'NOTWATCHING'
  }

  @property
  def map(self):
    return self.MAP


class State(NamedValue):
  MAP = {
    0: 'UNKNOWN',
    zookeeper.CONNECTING_STATE: 'CONNECTING',
    zookeeper.ASSOCIATING_STATE: 'ASSOCIATING',
    zookeeper.CONNECTED_STATE: 'CONNECTED',
    zookeeper.EXPIRED_SESSION_STATE: 'EXPIRED_SESSION',
    zookeeper.AUTH_FAILED_STATE: 'AUTH_FAILED',
  }

  @property
  def map(self):
    return self.MAP


class ReturnCode(NamedValue):
  MAP = {
    # Normal
    zookeeper.OK: 'OK',

    # Abnormal
    zookeeper.NONODE: 'NONODE',
    zookeeper.NOAUTH: 'NOAUTH',
    zookeeper.BADVERSION: 'BADVERSION',
    zookeeper.NOCHILDRENFOREPHEMERALS: 'NOCHILDRENFOREPHEMERALS',
    zookeeper.NODEEXISTS: 'NODEEXISTS',
    zookeeper.NOTEMPTY: 'NOTEMPTY',
    zookeeper.SESSIONEXPIRED: 'SESSIONEXPIRED',
    zookeeper.INVALIDCALLBACK: 'INVALIDCALLBACK',
    zookeeper.INVALIDACL: 'INVALIDACL',
    zookeeper.AUTHFAILED: 'AUTHFAILED',
    zookeeper.CLOSING: 'CLOSING',
    zookeeper.NOTHING: 'NOTHING',
    zookeeper.SESSIONMOVED: 'SESSIONMOVED',

    # Exceptional
    zookeeper.SYSTEMERROR: 'SYSTEMERROR',
    zookeeper.RUNTIMEINCONSISTENCY: 'RUNTIMEINCONSISTENCY',
    zookeeper.DATAINCONSISTENCY: 'DATAINCONSISTENCY',
    zookeeper.CONNECTIONLOSS: 'CONNECTIONLOSS',
    zookeeper.MARSHALLINGERROR: 'MARSHALLINGERROR',
    zookeeper.UNIMPLEMENTED: 'UNIMPLEMENTED',
    zookeeper.OPERATIONTIMEOUT: 'OPERATIONTIMEOUT',
    zookeeper.BADARGUMENTS: 'BADARGUMENTS',
    zookeeper.INVALIDSTATE: 'INVALIDSTATE'
  }

  @property
  def map(self):
    return self.MAP


class Id(object):
  def __init__(self, scheme, id_):
    if not isinstance(scheme, str) or not isinstance(id_, str):
      raise ValueError('Scheme and id must be strings!')
    self.scheme = scheme
    self.id = id_


class Acl(dict):
  def __init__(self, perm, id_):
    dict.__init__(self)
    self['perms'] = perm
    self['scheme'] = id_.scheme
    self['id'] = id_.id
