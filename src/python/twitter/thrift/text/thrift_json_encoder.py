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

class ThriftJSONEncoder(json.JSONEncoder):
  """An encoder that makes python thrift structs JSON serializable via the
  standard python json module.

  Pass this encoder when writing json, like this:

  json.dumps(thriftobj, cls=text.ThriftJSONEncoder, <other kwargs>)

  Note that this is not a full protocol implementation in the thrift sense. This
  is just a quick-and-easy pretty-printer for unittests, debugging etc.
  """
  THRIFT_SPEC = 'thrift_spec'

  def __init__(self, *args, **kwargs):
    super(ThriftJSONEncoder, self).__init__(*args, **kwargs)

  def default(self, o):
    # Handle sets (the value type of a thrift set field). We emit them as lists,
    # sorted by element.
    if isinstance(o, set):
      ret = list(o)
      ret.sort()
      return ret

    # Handle everything that isn't a thrift struct.
    if not hasattr(o, ThriftJSONEncoder.THRIFT_SPEC):
      return super(ThriftJSONEncoder, self).default(o)

    # Handle thrift structs.
    spec = getattr(o, ThriftJSONEncoder.THRIFT_SPEC)
    ret = {}
    for (field_number, type, name, type_info, default) in \
          [field_spec for field_spec in spec if field_spec is not None]:
      if name in o.__dict__:
        val = o.__dict__[name]
        if val != default:
          ret[name] = val
    return ret

def thrift_to_json(o):
  """A utility shortcut function to return a pretty-printed JSON thrift object.

  Map keys will be sorted, making the returned string suitable for use in test comparisons."""
  # ident=2 tells python to pretty-print, and put a newline after each comma. Therefore we
  # set the comma separator to have no space after it. Otherwise it'll be difficult to embed
  # a golden string for comparisons (since our editors strip spaces at the ends of lines).
  return json.dumps(o, sort_keys=True, cls=ThriftJSONEncoder, indent=2, separators=(',', ': '))

