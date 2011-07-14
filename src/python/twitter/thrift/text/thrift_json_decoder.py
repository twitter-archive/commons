# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

import json

from thrift.Thrift import TType

class ThriftJSONDecoder(json.JSONDecoder):
  """A decoder that makes python thrift structs JSON deserializable via the
  standard python json module.

  Pass this decoder when reading json, like this:

  json.loads(str, cls=text.ThriftJSONDecoder, <other kwargs>)

  Note that this is not a full protocol implementation in the thrift sense. This
  is just a quick-and-easy parser for unittests etc.
  """
  ROOT_THRIFT_CLASS = 'root_thrift_class'

  def __init__(self, *args, **kwargs):
    self.root_thrift_class = kwargs[ThriftJSONDecoder.ROOT_THRIFT_CLASS]
    del kwargs[ThriftJSONDecoder.ROOT_THRIFT_CLASS]
    super(ThriftJSONDecoder, self).__init__(*args, **kwargs)

  def decode(self, json_str):
    dict = super(ThriftJSONDecoder, self).decode(json_str)
    return self._convert(dict, TType.STRUCT,
                         (self.root_thrift_class, self.root_thrift_class.thrift_spec))

  def _convert(self, val, ttype, ttype_info):
    if ttype == TType.STRUCT:
      (thrift_class, thrift_spec) = ttype_info
      ret = thrift_class()
      for field in thrift_spec:
        if field is not None:
          (tag, field_ttype, field_name, field_ttype_info, dummy) = field
          if field_name in val:
            converted_val = self._convert(val[field_name], field_ttype, field_ttype_info)
            setattr(ret, field_name, converted_val)
    elif ttype == TType.LIST:
      (element_ttype, element_ttype_info) = ttype_info
      ret = [self._convert(x, element_ttype, element_ttype_info) for x in val]
    elif ttype == TType.SET:
      (element_ttype, element_ttype_info) = ttype_info
      ret = set([self._convert(x, element_ttype, element_ttype_info) for x in val])
    elif ttype == TType.MAP:
      (key_ttype, key_ttype_info, val_ttype, val_ttype_info) = ttype_info
      ret = dict([(self._convert(k, key_ttype, key_ttype_info),
                   self._convert(v, val_ttype, val_ttype_info)) for (k, v) in val.iteritems()])
    elif ttype == TType.STRING:
      ret = unicode(val)
    elif ttype == TType.DOUBLE:
      ret = float(val)
    elif ttype == TType.I64:
      ret = long(val)
    elif ttype == TType.I32 or ttype == TType.I16 or ttype == TType.BYTE:
      ret = int(val)
    elif ttype == TType.BOOL:
      ret = not not val
    else:
      raise Exception, 'Unrecognized thrift field type: %d' % ttype
    return ret

def json_to_thrift(json_str, root_thrift_class):
  """A utility shortcut function to parse a thrift json object of the specified class."""
  return json.loads(json_str, cls=ThriftJSONDecoder, root_thrift_class=root_thrift_class)

