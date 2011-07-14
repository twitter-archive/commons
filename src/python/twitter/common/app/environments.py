from optparse import OptionValueError
from types import IntType, StringTypes

class _Env(object):
  TEST        = 1
  DEVELOPMENT = 2
  PRODUCTION  = 3

  _str_to_id = {
    'TEST': TEST,
    'DEVELOPMENT': DEVELOPMENT,
    'PRODUCTION': PRODUCTION
  }

  _id_to_str = dict(reversed(entry) for entry in _str_to_id.items())

  @staticmethod
  def to_str(id):
    assert id in _Env._id_to_str
    return _Env._id_to_str[id]

  @staticmethod
  def to_id(str):
    assert str in _Env._str_to_id
    return _Env._str_to_id[str]

class Environment(object):
  TEST = _Env.TEST
  DEVELOPMENT = _Env.DEVELOPMENT
  PRODUCTION = _Env.PRODUCTION

  @staticmethod
  def _option_parser(option, opt, value, parser):
    if value.upper() not in _Env._str_to_id:
      raise OptionValueError('Invalid environment: %s, should be one of: %s' % (
        value, ' '.join(_Env._str_to_id.keys())))
    setattr(parser.values, option.dest, _Env.to_id(value.upper()))

  @staticmethod
  def names():
    return _Env._str_to_id.keys()
