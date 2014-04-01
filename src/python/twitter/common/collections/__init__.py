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

from sys import version_info

from twitter.common.lang import Compatibility

if version_info[0] == 2:
  from .ordereddict import OrderedDict
else:
  from collections import OrderedDict
from .orderedset import OrderedSet
from .ringbuffer import RingBuffer


def maybe_list(value, expected_type=Compatibility.string, raise_type=ValueError):
  """Given a value that could be a single value or iterable of a particular type, always return a
  list of that type.

  By default the expected type is a string/unicode, but can be specified with the 'expected_type' kwarg,
  which can be a type or tuple of types.

  By default raises ValueError on 'expected_type' mismatch, but can be specified with the
  'raise_type' kwarg.

  Raises ValueError if any type mismatches.
  """
  from collections import Iterable
  if isinstance(value, expected_type):
    return [value]
  elif isinstance(value, Iterable):
    real_values = list(value)
    for v in real_values:
      if not isinstance(v, expected_type):
        raise raise_type('Expected elements of list to be %r, got value %s of type %r' %
                         (expected_type, v, type(v)))
    return real_values
  else:
    raise raise_type('Value must be of type %r or iterable of type %r' %
                     (expected_type, expected_type))


__all__ = (
  maybe_list,
  OrderedSet,
  OrderedDict,
  RingBuffer,
)
