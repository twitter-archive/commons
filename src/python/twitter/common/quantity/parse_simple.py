from optparse import OptionValueError

from twitter.common.lang import Compatibility
from twitter.common.quantity import Data, Time, Amount


class InvalidTime(ValueError):
  def __init__(self, timestring):
    ValueError.__init__(self, "Invalid time span: %s" % timestring)


def parse_time(timestring):
  """
    Parse a time string of the format
      XdYhZmWs (each field optional but must be in that order.)
  """
  if not isinstance(timestring, Compatibility.string):
    raise TypeError('timestring should be of type string')
  BASES = (('d', Time.DAYS), ('h', Time.HOURS), ('m', Time.MINUTES), ('s', Time.SECONDS))
  timestr = timestring.lower()
  total_time = Amount(0, Time.SECONDS)
  for base_char, base in BASES:
    timesplit = timestr.split(base_char)
    if len(timesplit) > 2:
      raise InvalidTime(timestring)
    if len(timesplit) == 2:
      try:
        amount = int(timesplit[0])
      except ValueError:
        raise InvalidTime(timestring)
      total_time = total_time + Amount(amount, base)
      timestr = timesplit[1]
  if len(timestr) != 0:
    raise InvalidTime(timestring)
  return total_time




class InvalidData(ValueError):
  def __init__(self, datastring):
    ValueError.__init__(self, "Invalid size: %s" % datastring)


def parse_data(datastring):
  """
    Parse a data string of the format:
    [integer][unit]
    where unit is in upper/lowercase k, kb, m, mb, g, gb, t, tb
  """
  if not isinstance(datastring, Compatibility.string):
    raise InvalidData('parse_data takes a string, got %s' % type(datastring))

  datastring = datastring.strip().lower()

  try:
    return Amount(int(datastring), Data.BYTES)
  except ValueError:
    pass

  BASES = { 'k': Data.KB,
            'kb': Data.KB,
            'm': Data.MB,
            'mb': Data.MB,
            'g': Data.GB,
            'gb': Data.GB,
            't': Data.TB,
            'tb': Data.TB }
  for base in BASES:
    if datastring.endswith(base):
      try:
        return Amount(int(datastring[:-len(base)]), BASES[base])
      except ValueError as e:
        raise InvalidData('Could not parse amount: %s' % e)
  raise InvalidData('Amount did not have a valid base: %s.  Valid bases: %s' % (
      datastring, ' '.join(BASES)))


def parse_amount_into(parse_function, option_name, default=None):
  def parse_amount_callback(option, opt, value, parser):
    try:
      setattr(parser.values, option_name, parse_function(value or default))
    except Exception as e:
      raise OptionValueError('Failed to parse: %s' % e)
  return parse_amount_callback


def parse_time_into(option_name, default=None):
  """
    An optparse-compatible callback for populating Amounts of Time.
  """
  return parse_amount_into(parse_time, option_name, default=default)


def parse_data_into(option_name, default=None):
  """
    An optparse-compatible callback for populating Amounts of Data.
  """
  return parse_amount_into(parse_data, option_name, default=default)
