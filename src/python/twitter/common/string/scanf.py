import re
from ctypes import (
  c_int,
  c_long,
  c_longlong,
  c_uint,
  c_ulong,
  c_ulonglong,
  c_float,
  c_double,
  c_char,
  c_char_p,
)

from twitter.common.lang import Compatibility


class ScanfResult(object):
  def __init__(self):
    self._dict = {}
    self._list = []

  def groups(self):
    """
      Matched named parameters.
    """
    return self._dict

  def __getattr__(self, key):
    if key in self._dict:
      return self._dict[key]
    else:
      raise AttributeError('Could not find attribute: %s' % key)

  def ungrouped(self):
    """
      Matched unnamed parameters.
    """
    return self._list

  def __iter__(self):
    return iter(self._list)


class ScanfParser(object):
  class ParseError(Exception): pass

  """
    Partial scanf emulator.
  """
  CONVERSIONS = {
     "c": (".", c_char),
     "d": ("[-+]?\d+", c_int),
     "ld": ("[-+]?\d+", c_long),
     "lld": ("[-+]?\d+", c_longlong),
     "f": (r"[-+]?[0-9]*\.?[0-9]*(?:[eE][-+]?[0-9]+)?", c_float),
     "s": ("\S+", c_char_p),
     "u": ("\d+", c_uint),
     "lu": ("\d+", c_ulong),
     "llu": ("\d+", c_ulonglong),
  }

  # ctypes don't do str->int conversion, so must preconvert for non-string types
  PRECONVERSIONS = {
    c_char: str,  # to cover cases like unicode
    c_int: int,
    c_long: int,
    c_longlong: long if Compatibility.PY2 else int,
    c_uint: int,
    c_ulong: int,
    c_ulonglong: long if Compatibility.PY2 else int,
    c_float: float,
    c_double: float
  }

  def _preprocess_format_string(self, string):
    def match_conversion(string, k):
      MAX_CONVERSION_LENGTH = 3
      for offset in range(MAX_CONVERSION_LENGTH, 0, -1):
        k_offset = k + offset
        if string[k:k_offset] in ScanfParser.CONVERSIONS:
          re, converter = ScanfParser.CONVERSIONS[string[k:k_offset]]
          if converter in ScanfParser.PRECONVERSIONS:
            return (re, lambda val: converter(ScanfParser.PRECONVERSIONS[converter](val))), k_offset
          else:
            return (re, converter), k_offset
      raise ScanfParser.ParseError('%s is an invalid format specifier' % (
        string[k]))

    def extract_specifier(string, k):
      if string[k] == '%':
        return '%', None, k+1
      if string[k] == '*':
        def null_apply(scan_object, value):
          pass
        (regex, preconversion), k = match_conversion(string, k+1)
        return '(%s)' % regex, null_apply, k
      if string[k] == '(':
        offset = string[k+1:].find(')')
        if offset == -1:
          raise ScanfParser.ParseError("Unmatched (")
        if offset == 0:
          raise ScanfParser.ParseError("Empty label string")
        name = string[k+1:k+1+offset]
        (regex, preconversion), k = match_conversion(string, k+1+offset+1)
        def dict_apply(scan_object, value):
          scan_object._dict[name] = preconversion(value).value
        return '(%s)' % regex, dict_apply, k
      (regex, preconversion), k = match_conversion(string, k)
      def list_apply(scan_object, value):
        scan_object._list.append(preconversion(value).value)
      return '(%s)' % regex, list_apply, k

    re_str = ""
    k = 0
    applicators = []
    while k < len(string):
      if string[k] == '%' and len(string) > k+1:
        regex, applicator, k = extract_specifier(string, k+1)
        re_str += regex
        if applicator:
          applicators.append(applicator)
      else:
        re_str += re.escape(string[k])
        k += 1
    return re_str, applicators

  def parse(self, line, allow_extra=False):
    """
      Given a line of text, parse it and return a ScanfResult object.
    """
    if not isinstance(line, Compatibility.string):
      raise TypeError("Expected line to be a string, got %s" % type(line))
    sre_match = self._re.match(line)
    if sre_match is None:
      raise ScanfParser.ParseError("Failed to match pattern: %s against %s" % (
        self._re_pattern, line))
    groups = list(sre_match.groups())
    if len(groups) != len(self._applicators):
      raise ScanfParser.ParseError("Did not parse all groups! Missing %d" % (
        len(self._applicators) - len(groups)))
    if sre_match.end() != len(line) and not allow_extra:
      raise ScanfParser.ParseError("Extra junk on the line! '%s'" % (
        line[sre_match.end():]))
    so = ScanfResult()
    for applicator, group in zip(self._applicators, groups):
      applicator(so, group)
    return so

  def __init__(self, format_string):
    """
      Given a format string, construct a parser.

      The format string takes:
        %c %d %u %f %s
        %d and %u take l or ll modifiers
        you can name parameters %(hey there)s and the string value will be keyed by "hey there"
        you can parse but not save parameters by specifying %*f
    """
    if not isinstance(format_string, Compatibility.string):
      raise TypeError('format_string should be a string, instead got %s' % type(format_string))
    self._re_pattern, self._applicators = self._preprocess_format_string(format_string)
    self._re = re.compile(self._re_pattern)
