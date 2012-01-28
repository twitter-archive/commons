import pytest
import unittest
from twitter.common.string.scanf import ScanfParser

def almost_equal(a, b, digits=7):
  return abs(a-b) < 0.1**digits

def scanf(fmt, string):
  formatter = ScanfParser(fmt)
  result = formatter.parse(string)
  return result.groups(), result.ungrouped()

def test_parsing_names():
  with pytest.raises(ScanfParser.ParseError):
    ScanfParser("%()s")
  with pytest.raises(ScanfParser.ParseError):
    ScanfParser("%(s")
  with pytest.raises(ScanfParser.ParseError):
    ScanfParser("%)s")
  ScanfParser("%")  # This is valid but could cause string overflow if parser not careful
  with pytest.raises(ScanfParser.ParseError):
    ScanfParser("% c")

def test_multi():
  # Regexes are powerful beasts.
  pairs = {
    "%f %c": "1.0 h",
    " %f %c": " 1.0 h",
    "%f %c ": "1.0 h ",
    " %f %c ": " 1.0 h ",
    "1%f %c": "11.0 h",
    " %f3%c": " 1.03h",
    "%f %c5": "1.0 h5",
    " %f %c '": " 1.0 h '",
  }

  for pattern, value in pairs.items():
    _, vals = scanf(pattern, value)
    assert len(vals) == 2, "%s has two values" % pattern
    assert almost_equal(vals[0], 1.0)
    assert vals[1] == 'h'

def test_mixed_multi_named_then_unnamed():
  pairs = {
    " %(val1)f %c": " 1.0 h",
    "1%(val1)f %c": "11.0 h",
    " %(val1)f3%c": " 1.03h",
  }

  for pattern, value in pairs.items():
    d, l = scanf(pattern, value)
    assert len(d) == 1
    assert len(l) == 1
    assert almost_equal(d['val1'], 1.0)
    assert l[0] == 'h'

def test_mixed_multi_unnamed_then_named():
  pairs = {
    "%f %(val1)c": "1.0 h",
    " %f3%(val1)c": " 1.03h",
    "%f %(val1)c5": "1.0 h5",
  }

  for pattern, value in pairs.items():
    d, l = scanf(pattern, value)
    assert len(d) == 1
    assert len(l) == 1
    assert almost_equal(l[0], 1.0)
    assert d['val1'] == 'h'

def test_mixed_ignored():
  pairs = {
    "%*f %(val1)c": "1.0 h",
    " %*f %(val1)c": " 1.0 h",
    "1%*f %(val1)c": "11.0 h",
    " %*f3%(val1)c": " 1.03h",
  }

  for pattern, value in pairs.items():
    d, l = scanf(pattern, value)
    assert len(d) == 1
    assert len(l) == 0
    assert d['val1'] == 'h'

def test_many():
  # named
  d, l = scanf("%(a)c %(b)c %(c)c %(d)c", "a b c d")
  assert len(l) == 0
  assert len(d) == 4
  for ch in 'abcd':
    assert d[ch] == ch

  # unnamed
  d, l = scanf("%c%c%c %c", "abc d")
  assert len(l) == 4
  assert len(d) == 0
  for val, idx in zip('abcd', range(4)):
    assert l[idx] == val


def test_weird_names():
  weird_names = ['{', '}', '{}', '(', '*', '%c', '%%', ' ', 'w u hhhht', '123', '^', ',']
  # named
  for weird_name in weird_names:
    d, l = scanf("%(" + weird_name + ")s", "whee!")
  assert len(l) == 0
  assert len(d) == 1
  assert weird_name in d
  assert d[weird_name] == 'whee!'
