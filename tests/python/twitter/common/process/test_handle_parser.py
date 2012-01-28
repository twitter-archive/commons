import unittest
import pytest
from twitter.common.process.process_handle import ProcessHandleParser

class TestHandleParser(unittest.TestCase):
  @classmethod
  def setup_class(cls):
    cls.attrs = "first last year_of_birth day_of_birth".split()
    cls.type_map = {
      'first': '%s',
      'last': '%s',
      'year_of_birth': '%d',
      'day_of_birth': '%d'
    }
    cls.days_of_week = ['m', 't', 'w', 'th', 'f', 's', 'su']
    cls.handlers = {
      'first': lambda k, v: v.upper(),
      'last': lambda k, v: v.lower(),
      'day_of_birth': lambda k, v: cls.days_of_week[v % len(cls.days_of_week)]
    }
    cls.ph = ProcessHandleParser(cls.attrs, cls.type_map, cls.handlers)

  def test_basic(self):
    test_strings = [
      'john doe 1900 23',
      'JoHn DoE 1900 23',
      'JoHn\tDoE\t1900\t23',
      '   JoHn \t DoE \t1900\t 23 ',
    ]
    for test_string in test_strings:
      attrs = self.ph.parse(test_string)
      assert attrs['first'] == 'JOHN'
      assert attrs['last'] == 'doe'
      assert attrs['year_of_birth'] == 1900
      assert attrs['day_of_birth'] == self.days_of_week[23 % len(self.days_of_week)]
