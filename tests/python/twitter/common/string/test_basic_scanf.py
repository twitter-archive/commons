import pytest
import unittest
from twitter.common.string.scanf import ScanfParser

def almost_equal(a, b, digits=7):
  return abs(a-b) < 0.1**digits

def basic_scanf(fmt, string, extra=False):
  formatter = ScanfParser(fmt)
  result = formatter.parse(string, extra)
  assert len(result.ungrouped()) == 1
  return result.ungrouped()[0]

def test_bad_input():
  conversions = ScanfParser.CONVERSIONS.keys()
  bad_stuff = [
    " a", " 1", " +",
    "a ", "1 ", "+ ",
  ]
  garbage_stuff = [
    0, 1, None, dir, [], {}, (), type
  ]

  for c in conversions:
    for b in bad_stuff:
      with pytest.raises(ScanfParser.ParseError):
        basic_scanf(c, b)
    for b in garbage_stuff:
      with pytest.raises(TypeError):
        basic_scanf(c, b)

def test_no_matches():
  print ScanfParser("%%")._re_pattern
  match = ScanfParser("%%").parse("%")
  assert len(match.groups()) == 0
  assert len(match.ungrouped()) == 0

  test_strings = ["a", " ", "hello hello", "1.0 hello nothing to see here move along", ""]
  for t_s in test_strings:
    match = ScanfParser(t_s).parse(t_s)
    assert len(match.groups()) == 0
    assert len(match.ungrouped()) == 0

def test_garbage_formats():
  garbage_input = [0, 1, None, dir, [], {}, (), type]
  for garbage in garbage_input:
    with pytest.raises(TypeError):
      ScanfParser(garbage)

def test_special_characters():
  special_stuffs = [
    (')', '('),
    ('(', ')'),  ('[', ']'),     ('{', '}'),
    ('(', ')+'),
    ('(|', ')'),
    ('{,', '}'),
    ('$', '^'), ('^', '$'),
    (' ', '+'), (' ', '*'), (' ', '?')
  ]
  for before, after in special_stuffs:
    assert basic_scanf(before+'%c'+after, before+'a'+after) == 'a'
    assert basic_scanf(before+'%c'+after, before+u'a'+after) == 'a'
    assert basic_scanf(before+'%c'+after, before+' '+after) == ' '

def test_character_conversion():
  assert basic_scanf('%c', 'a') == 'a'
  assert basic_scanf('%c', u'a') == 'a'
  assert basic_scanf('%c', ' ') == ' '

def test_integer_conversion():
  for conversion in ('%d', '%ld', '%lld'):
    assert basic_scanf(conversion, '1') == 1
    assert basic_scanf(conversion, '01') == 1
    assert basic_scanf(conversion, '+01') == 1
    assert basic_scanf(conversion, '-01') == -1

def test_failing_integer_conversion():
  with pytest.raises(ScanfParser.ParseError):
    basic_scanf('%d', "\x90")
  with pytest.raises(ScanfParser.ParseError):
    basic_scanf('%d', "x")
  with pytest.raises(ScanfParser.ParseError):
    basic_scanf('%d', "hello")

def test_long_conversion():
  for conversion in ('%u', '%lu', '%llu'):
    assert basic_scanf(conversion, '1') == 1
    assert basic_scanf(conversion, '01') == 1

def test_float_conversion():
  factor_tests = {
    '': 1.0,
    'e-0': 1.0,
    'e-1': 0.1,
    'e+1': 10.0,
    'e1': 10.0,
    'e0': 1.0,
    'e5': 1.e5,
  }
  for exponent, xfactor in factor_tests.items():
    assert almost_equal(basic_scanf('%f', '0' + exponent), 0 * xfactor)
    assert almost_equal(basic_scanf('%f', '.1' + exponent), .1 * xfactor)
    assert almost_equal(basic_scanf('%f', '2.' + exponent), 2 * xfactor)
    assert almost_equal(basic_scanf('%f', '3.4' + exponent), 3.4 * xfactor)
    assert almost_equal(basic_scanf('%f', '-.5' + exponent), -0.5 * xfactor)

def test_string_conversion():
  for st in ('a', u'a', '123', u'123', 'a\x12\x23'):
    assert basic_scanf('%s', st) == st
  assert basic_scanf('%s', '\x00') == ''

def test_extra_stuff():
  extra_stuff = [ ' ', ' a', ' a b', ' $']
  for extra in extra_stuff:
    for st in ('a', u'a', '123', u'123', 'a\x12\x23'):
      assert basic_scanf('%s', st+extra, extra=True) == st

