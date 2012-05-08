from abc import ABCMeta, abstractproperty
from twitter.common.lang import Compatibility


class NamedValue(object):
  __metaclass__ = ABCMeta

  def __init__(self, value):
    if isinstance(value, int):
      self._value = value if value in self.map else 0
    elif isinstance(value, Compatibility.string):
      self._value = dict((v, k) for (k, v) in self.map.items()).get(value.upper(), 0)
    else:
      raise ValueError('Unknown value: %s' % value)

  @abstractproperty
  def map(self):
    """Returns the map from id => string"""
    pass

  def __str__(self):
    return self.map.get(self._value, 'UNKNOWN')

  def __repr__(self):
    return '%s(%r)' % (self.__class__.__name__, self.map[self._value])
