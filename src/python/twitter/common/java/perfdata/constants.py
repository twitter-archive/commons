# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

class Units(object):
  INVALID = 0
  NONE = 1
  BYTES = 2
  TICKS = 3
  EVENTS = 4
  STRING = 5
  HERTZ = 6


class Variability(object):
  INVALID = 0
  CONSTANT = 1
  MONOTONIC = 2
  VARIABLE = 3


class TypeCode(object):
  BOOLEAN = 0
  CHAR = 1
  FLOAT = 2
  DOUBLE = 3
  BYTE = 4
  SHORT = 5
  INT = 6
  LONG = 7
  OBJECT = 8
  ARRAY = 9
  VOID = 10

  MAP = {
    'Z': (bool, BOOLEAN),
    'C': (str, CHAR),
    'F': (float, FLOAT),
    'D': (float, DOUBLE),
    'B': (int, BYTE),
    'S': (int, SHORT),
    'I': (int, INT),
    'J': (int, LONG),
    'L': (str, OBJECT),
    '[': (str, ARRAY),
    'V': (str, VOID),
  }

  @classmethod
  def to_code(cls, b):
    try:
      return cls.MAP[b][1]
    except KeyError:
      raise ValueError('Unknown TypeCode: %r' % b)
