# Copyright 2011 Twitter Inc. All rights reserved

__author__ = 'ugo'  # Ugo Di Girolamo

import traceback
from thrift.Thrift import TType

def _type_name(ftype):
  return TType._VALUES_TO_NAMES[ftype]

def read(iprot):
  """Reads arbitrary thrift structs from a thrift protocol.

  Returns a dictionary where:
    - keys are constructed from the id of the field as "FIELD_" + id
    - values are a tuple (type_name, data) where data is:
      - in the case of STRUCTs the dictionary created calling this function
      - in the case of simple data (I*, STRING, DOUBLE, ...) just the value
      - for containters, respectively:
        - a tuple ((key_type_name, value_type_name), dict)
        - a tuple (value_type_name, list)
        - a tuple (value_type_name, set)
        where the elements in the dict, list and set are parsed using the same
        rules.
  """
  data = {}
  iprot.readStructBegin()
  while True:
    (fname, ftype, fid) = iprot.readFieldBegin()
    if ftype == TType.STOP:
      break
    else:
      try:
        val = _read_one_field(iprot, ftype)
        data["FIELD_%d" % fid] = (_type_name(ftype), val)
      except:
        traceback.print_exc()
    iprot.readFieldEnd()
  iprot.readStructEnd()
  return data

def _read_one_field(iprot, ftype):
  if ftype == TType.BOOL:
    return iprot.readBool()
  elif ftype == TType.BYTE:
    return iprot.readByte()
  elif ftype == TType.I08:
    return iprot.readI08()
  elif ftype == TType.I16:
    return iprot.readI16()
  elif ftype == TType.I32:
    return iprot.readI32()
  elif ftype == TType.I64:
    return iprot.readI64()
  elif ftype == TType.DOUBLE:
    return iprot.readDouble()
  elif ftype == TType.STRING:
    return iprot.readString()
  elif ftype == TType.UTF8:
    return iprot.readUtf8()
  elif ftype == TType.UTF16:
    return iprot.readUtf16()
  elif ftype == TType.STRUCT:
    return read(iprot)
  elif ftype == TType.MAP:
    (ktype, vtype, size) = iprot.readMapBegin()
    data = {}
    for i in range(size):
      k = _read_one_field(iprot, ktype)
      v = _read_one_field(iprot, vtype)
      data[k] = v
    iprot.readMapEnd()
    return ((_type_name(ktype), _type_name(vtype)), data)
  elif ftype == TType.SET:
    (etype, size) = iprot.readSetBegin()
    data = set()
    for i in range(size):
      data.add(_read_one_field(iprot, etype))
    iprot.readSetEnd()
    return (_type_name(etype), data)
  elif ftype == TType.LIST:
    (etype, size) = iprot.readListBegin()
    data = list()
    for i in range(size):
      data.append(_read_one_field(iprot, etype))
    iprot.readListEnd()
    return (_type_name(etype), data)
