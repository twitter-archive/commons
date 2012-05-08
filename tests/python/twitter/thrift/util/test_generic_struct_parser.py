# Copyright 2011 Twitter Inc. All rights reserved

__author__ = 'ugo'  # Ugo Di Girolamo

"""Tests for the generic_struct_parser class."""

import unittest

from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol
from twitter.thrift.util import generic_struct_parser
from gen.twitter.thrift.text.testing import ttypes as structs_for_testing

class GenericStructParserTest(unittest.TestCase):
  def test_read_binary_encoded(self):
    x = structs_for_testing.TestStruct()
    x.field2 = True
    x.field4 = [2, 4, 6, 8]
    x.field7 = 1.2
    x.field1 = 42
    x.field2 = False
    x.field3 = '"not default"'
    x.field4.append(10)
    x.field5 = set(['b', 'c', 'a'])
    x.field6 = structs_for_testing.InnerTestStruct()
    x.field6.foo = "bar"
    x.field6.color = structs_for_testing.Color.BLUE

    otransport = TTransport.TMemoryBuffer()
    oprot = TBinaryProtocol.TBinaryProtocol(otransport)
    x.write(oprot)
    itransport = TTransport.TMemoryBuffer(otransport.cstringio_buf.getvalue())
    iprot = TBinaryProtocol.TBinaryProtocol(itransport)

    actual = generic_struct_parser.read(iprot)
    expected_field6 = {"FIELD_1": ('STRING', "bar"),
                       "FIELD_2": ('I32', structs_for_testing.Color.BLUE),
                      }
    expected = {"FIELD_1": ('I32', 42),
                "FIELD_2": ('BOOL', False),
                "FIELD_3": ('STRING', '"not default"'),
                "FIELD_4": ('LIST', ('I16', list([2, 4, 6, 8, 10]))),
                "FIELD_5": ('SET', ('STRING', set(['a', 'b', 'c']))),
                "FIELD_6": ('STRUCT', expected_field6),
                "FIELD_7": ('DOUBLE', 1.2),
               }
    print("actual   = %s" % actual)
    print("expected = %s" % expected)
    # Note(ugo): assertDictEqual is only in python 2.7
    try:
      self.assertEquals(actual, expected)
    except AssertionError as e:
      all_keys = set(expected.keys())
      all_keys.union(actual.keys())
      print("differences:")
      for k in all_keys:
        a = actual.get(k, '__missing__')
        e = expected.get(k, '__missing__')
        if a != e:
          print('  %r: %s vs %s' % (k, a, e))
      raise
