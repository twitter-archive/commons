__author__ = 'Brian Wickman'

from .scanf import ScanfParser, ScanfResult

def basic_scanf(fmt_string, value_string):
  """
    Given format string, parse value string and return list of extracted
    variables.  Does not support named variables.

    See ScanfParser class for variable description.
  """
  so = ScanfParser(fmt_string).parse(value_string)
  if len(so.groups()) > 0:
    raise ScanfParser.ParseError("basic_scanf does not support named arguments!")
  return so.ungrouped()

def scanf(fmt_string, value_string):
  return ScanfParser(fmt_string).parse(value_string)

__all__ = [
  'scanf',
  'basic_scanf',
  'ScanfParser',
  'ScanfResult'
]
