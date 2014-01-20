from twitter.common.lang import Compatibility

class Address(object):
  class InvalidFormat(Exception):
    pass

  @staticmethod
  def sanity_check(host, port):
    if not isinstance(host, Compatibility.string):
      raise Address.InvalidFormat('Host must be a string, got %s' % host)
    if not isinstance(port, (int, long)):
      raise Address.InvalidFormat('Port must be an integer, got %s' % port)
    if port <= 0:
      raise Address.InvalidFormat('Port must be a positive integer, got %s' % port)

  @staticmethod
  def from_string(*args, **kw):
    if (kw or len(args) != 1 or not isinstance(args[0], Compatibility.string)
        or not len(args[0].split(':')) == 2):
      raise Address.InvalidFormat('from_string expects "host:port" string.')
    host, port = args[0].split(':')
    try:
      port = int(port)
    except ValueError:
      raise Address.InvalidFormat('Port must be an integer, got %s' % port)
    Address.sanity_check(host, port)
    return Address(host, port)

  @staticmethod
  def from_pair(*args, **kw):
    if (kw or len(args) != 2 or not isinstance(args[0], Compatibility.string)
        or not isinstance(args[1], (int, long))):
      raise Address.InvalidFormat('from_pair expects host, port as input!')
    Address.sanity_check(args[0], args[1])
    return Address(args[0], args[1])

  @staticmethod
  def from_tuple(*args, **kw):
    if kw or len(args) != 1 or len(args[0]) != 2:
      raise Address.InvalidFormat('from_tuple expects (host, port) tuple as input!')
    host, port = args[0]
    Address.sanity_check(host, port)
    return Address(host, port)

  @staticmethod
  def from_address(*args, **kw):
    if kw or len(args) != 1 or not isinstance(args[0], Address):
      raise Address.InvalidFormat('from_address expects an address as input!')
    return Address(args[0].host, args[0].port)

  @staticmethod
  def parse(*args, **kw):
    for parser in [Address.from_string, Address.from_pair, Address.from_address, Address.from_tuple]:
      try:
        return parser(*args, **kw)
      except Address.InvalidFormat:
        continue
    raise Address.InvalidFormat('Could not parse input: args=%s kw=%s' % (
      repr(args), repr(kw)))

  def __init__(self, host, port):
    self._host = host
    self._port = port

  @property
  def host(self):
    return self._host

  @property
  def port(self):
    return self._port
