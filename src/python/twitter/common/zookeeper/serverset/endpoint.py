import json
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
    return cls(blob.host, blob.port)

  @classmethod
  def to_dict(cls, endpoint):
    return {
      'host': endpoint.host,
      'port': endpoint.port
    }

  def __init__(self, host, port):
    if not isinstance(host, Compatibility.string):
      raise ValueError('Expected host to be a string!')
    if not isinstance(port, int):
      raise ValueError('Expected port to be an integer!')
    self._host = host
    self._port = port

  def __key(self):
    return (self.host, self.port)

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
  def unpack(cls, blob):
    try:
      return cls.unpack_json(blob)
    except Exception as e1:
      try:
        return cls.unpack_thrift(blob)
      except Exception as e2:
        log.debug('Failed to deserialize JSON: %s (%s) && Thrift: %s (%s)' % (
          e1, e1.__class__.__name__, e2, e2.__class__.__name__))
        return None

  @classmethod
  def unpack_json(cls, blob):
    blob = json.loads(blob)
    for key in ('status', 'serviceEndpoint', 'additionalEndpoints'):
      if key not in blob:
        raise ValueError('Expected to find %s in ServiceInstance JSON!' % key)
    additional_endpoints = dict((name, Endpoint(value['host'], value['port']))
      for name, value in blob['additionalEndpoints'].items())
    shard = blob.get('shard')
    if shard is not None:
      try:
        shard = int(shard)
      except ValueError:
        log.warn('Failed to deserialize shard from value %r' % shard)
        shard = None
    return cls(
      service_endpoint=Endpoint(blob['serviceEndpoint']['host'], blob['serviceEndpoint']['port']),
      additional_endpoints=additional_endpoints,
      status=Status.from_string(blob['status']),
      shard=shard)

  @classmethod
  def unpack_thrift(cls, blob):
    if not isinstance(blob, ThriftServiceInstance):
      blob = thrift_deserialize(ThriftServiceInstance(), blob)
    additional_endpoints = dict((name, Endpoint.unpack_thrift(value))
      for name, value in blob.additionalEndpoints.items())
    return cls(
      service_endpoint=Endpoint.unpack_thrift(blob.serviceEndpoint),
      additional_endpoints=additional_endpoints,
      status=Status.from_thrift(blob.status),
      shard=blob.shard)

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
    return instance

  @classmethod
  def pack(cls, service_instance):
    return json.dumps(cls.to_dict(service_instance))

  def __init__(self, service_endpoint, additional_endpoints=None, status='ALIVE', shard=None):
    if not isinstance(service_endpoint, Endpoint):
      raise ValueError('Expected service_endpoint to be an Endpoint, got %r' % service_endpoint)
    self._shard = shard
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

  def __additional_endpoints_string(self):
    return ['%s=>%s' % (key, val) for key, val in self.additional_endpoints.items()]

  def __key(self):
    return (
        self.service_endpoint,
        frozenset(sorted(self.__additional_endpoints_string())),
        self.status,
        self._shard)

  def __eq__(self, other):
    return isinstance(other, self.__class__) and self.__key() == other.__key()

  def __hash__(self):
    return hash(self.__key())


  def __str__(self):
    return 'ServiceInstance(%s, %saddl: %s, status: %s)' % (
      self.service_endpoint,
      ('shard: %s, ' % self._shard) if self._shard is not None else '',
      ' : '.join(self.__additional_endpoints_string()),
      self.status)
