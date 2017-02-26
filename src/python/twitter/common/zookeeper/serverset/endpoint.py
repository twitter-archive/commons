import json
import socket
from thrift.TSerialization import deserialize as thrift_deserialize

from gen.twitter.thrift.endpoint.ttypes import (
  Endpoint as ThriftEndpoint,
  ServiceInstance as ThriftServiceInstance)

try:
  from twitter.common import log
except ImportError:
  import logging as log

from twitter.common.lang import Compatibility


class Endpoint(object):
  @classmethod
  def unpack_thrift(cls, blob):
    return cls(blob.host, blob.port, blob.inet, blob.inet6)

  @classmethod
  def from_dict(cls, value):
    inet = value.get('inet')
    inet6 = value.get('inet6')
    return Endpoint(value['host'], value['port'], inet, inet6)

  @classmethod
  def to_dict(cls, endpoint):
    d = {
      'host': endpoint.host,
      'port': endpoint.port
    }
    if endpoint.inet is not None:
      d['inet'] = endpoint.inet
    if endpoint.inet6 is not None:
      d['inet6'] = endpoint.inet6
    return d

  def __init__(self, host, port, inet=None, inet6=None):
    """host -- the hostname of the device where this endpoint can be found
       port -- the port number the device serves this endpoint on
       inet -- a human-readable representation of the  IPv4 address of the device
               where this endpoint can be found.
       inet6 -- a human-readable representation of the IPv6 address of the device
                where this endpoint can be found.

    `inet` and/or `inet6` can be used when present to avoid a DNS lookup.

    """
    if not isinstance(host, Compatibility.string):
      raise ValueError('Expected host to be a string!')
    if not isinstance(port, int):
      raise ValueError('Expected port to be an integer!')
    if inet is not None:
      try:
        socket.inet_pton(socket.AF_INET, inet)
      except socket.error:
        raise ValueError('Expected "%s" to be a string containing a valid IPv4 address!' % inet)
    if inet6 is not None:
      try:
        socket.inet_pton(socket.AF_INET6, inet6)
      except socket.error:
        raise ValueError('Expected "%s" to be a string containing a valid IPv6 address!' % inet6)

    self._host = host
    self._port = port
    self._inet = inet
    self._inet6 = inet6

  def __key(self):
    return (self.host, self.port, self._inet, self._inet6)

  def __eq__(self, other):
    return isinstance(other, self.__class__) and self.__key() == other.__key()

  def __hash__(self):
    return hash(self.__key())

  @property
  def host(self):
    return self._host

  @property
  def port(self):
    return self._port

  @property
  def inet(self):
    return self._inet

  @property
  def inet6(self):
    return self._inet6

  def __str__(self):
    return '%s:%s' % (self.host, self.port)


class Status(object):
  MAP = {
    0: 'DEAD',
    1: 'STARTING',
    2: 'ALIVE',
    3: 'STOPPING',
    4: 'STOPPED',
    5: 'WARNING'
  }

  @staticmethod
  def from_id(_id):
    if _id not in Status.MAP:
      raise ValueError('from_id got an invalid Status!')
    return Status(Status.MAP.get(_id), _id)

  @staticmethod
  def from_string(string):
    for _id, name in Status.MAP.items():
      if name == string:
        return Status(name, _id)
    raise ValueError('from_string got an invalid Status!')

  from_thrift = from_id

  def __init__(self, name, _id):
    self._name = name
    self._id = _id

  def name(self):
    return self._name

  def __eq__(self, other):
    return self._id == other._id

  def __hash__(self):
    return hash(self._id)

  def __str__(self):
    return self.name()


class ServiceInstance(object):
  class InvalidType(Exception): pass
  class UnknownEndpoint(Exception): pass

  @classmethod
  def unpack(cls, blob, member_id=None):
    """Return a ServiceInstance object from the json or thrift implementation of
      a serverset member.
      The member_id needs to be passed in separately because the info method from the group
      (either Group or KazooGroup) does not include it.
    """
    try:
      return cls.unpack_json(blob, member_id)
    except Exception as e1:
      try:
        return cls.unpack_thrift(blob, member_id)
      except Exception as e2:
        log.debug('Failed to deserialize JSON: %s (%s) && Thrift: %s (%s)' % (
          e1, e1.__class__.__name__, e2, e2.__class__.__name__))
        return None

  @classmethod
  def unpack_json(cls, blob, member_id=None):
    blob = json.loads(blob)
    for key in ('status', 'serviceEndpoint', 'additionalEndpoints'):
      if key not in blob:
        raise ValueError('Expected to find %s in ServiceInstance JSON!' % key)
    additional_endpoints = dict((name, Endpoint.from_dict(value))
      for name, value in blob['additionalEndpoints'].items())
    shard = cls.__check_int(blob.get('shard'))
    member_id = cls.__check_int(member_id)
    return cls(
      service_endpoint=Endpoint.from_dict(blob['serviceEndpoint']),
      additional_endpoints=additional_endpoints,
      status=Status.from_string(blob['status']),
      shard=shard,
      member_id=member_id)

  @classmethod
  def unpack_thrift(cls, blob, member_id=None):
    if not isinstance(blob, ThriftServiceInstance):
      blob = thrift_deserialize(ThriftServiceInstance(), blob)
    additional_endpoints = dict((name, Endpoint.unpack_thrift(value))
      for name, value in blob.additionalEndpoints.items())
    member_id = cls.__check_int(member_id)
    return cls(
      service_endpoint=Endpoint.unpack_thrift(blob.serviceEndpoint),
      additional_endpoints=additional_endpoints,
      status=Status.from_thrift(blob.status),
      shard=blob.shard,
      member_id=member_id)

  @classmethod
  def to_dict(cls, service_instance):
    instance = dict(
      serviceEndpoint=Endpoint.to_dict(service_instance.service_endpoint),
      additionalEndpoints=dict((name, Endpoint.to_dict(endpoint))
          for name, endpoint in service_instance.additional_endpoints.items()),
      status=service_instance.status.name()
    )
    if service_instance.shard is not None:
      instance.update(shard=service_instance.shard)
    if service_instance.member_id is not None:
      instance.update(member_id=service_instance.member_id)
    return instance

  @classmethod
  def pack(cls, service_instance):
    return json.dumps(cls.to_dict(service_instance))

  def __init__(
      self,
      service_endpoint,
      additional_endpoints=None,
      status='ALIVE',
      shard=None,
      member_id=None):
    if not isinstance(service_endpoint, Endpoint):
      raise ValueError('Expected service_endpoint to be an Endpoint, got %r' % service_endpoint)
    self._shard = shard
    self._member_id = member_id
    self._service_endpoint = service_endpoint
    self._additional_endpoints = additional_endpoints or {}
    if not isinstance(self._additional_endpoints, dict):
      raise ValueError('Additional endpoints must be a dictionary.')
    for name, endpoint in self._additional_endpoints.items():
      if not isinstance(name, Compatibility.string):
        raise ValueError('Expected additional endpoints to be named by strings.')
      if not isinstance(endpoint, Endpoint):
        raise ValueError('Endpoints must be of type Endpoint.')
    if isinstance(status, Compatibility.string):
      self._status = Status.from_string(status)
      if self._status is None:
        raise ValueError('Unknown status: %s' % status)
    elif isinstance(status, Status):
      self._status = status
    else:
      raise ValueError('Status must be of type ServiceInstance.Status or string.')

  @property
  def service_endpoint(self):
    return self._service_endpoint

  @property
  def additional_endpoints(self):
    return self._additional_endpoints

  @property
  def status(self):
    return self._status

  @property
  def shard(self):
    return self._shard

  @property
  def member_id(self):
    return self._member_id

  @staticmethod
  def __check_int(item):
    if item is not None:
      try:
        item = int(item)
      except ValueError:
        log.warn('Failed to deserialize value %r' % item)
        item = None
    return item

  def __additional_endpoints_string(self):
    return ['%s=>%s' % (key, val) for key, val in self.additional_endpoints.items()]

  def __key(self):
    return (
        self.service_endpoint,
        frozenset(sorted(self.__additional_endpoints_string())),
        self.status,
        self._shard,
        self._member_id)

  def __eq__(self, other):
    return isinstance(other, self.__class__) and self.__key() == other.__key()

  def __hash__(self):
    return hash(self.__key())

  def __str__(self):
    return 'ServiceInstance(%s, %s, %saddl: %s, status: %s)' % (
      self.service_endpoint,
      ('shard: %s, ' % self._shard) if self._shard is not None else '',
      ('member_id: %s, ' % self._member_id) if self._member_id is not None else '',
      ' : '.join(self.__additional_endpoints_string()),
      self.status)
