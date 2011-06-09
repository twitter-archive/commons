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

import sys
import inspect

from .recordio import RecordIO

try:
  import thrift.TSerialization as _SER
  _HAS_THRIFT = True
except ImportError:
  _SER = None
  _HAS_THRIFT = False
  print >> sys.stderr, "WARNING: Unable to load thrift in record_writer"

class ThriftRecordIO:
  class ThriftUnavailableException(Exception): pass
  class ThriftUnsuppliedException(Exception): pass
  class InvalidThriftException(Exception): pass

  @staticmethod
  def assert_has_thrift():
    if not _HAS_THRIFT:
      raise ThriftRecordIO.ThriftUnavailableException(
        "thrift module not available for serialization!")

  class ThriftCodec(RecordIO.Codec):
    """
      Thrift Codec.

      If no thrift_base is supplied, this codec may be used correctly in
      encode-only mode (i.e. for RecordWriters.)
    """

    def __init__(self, thrift_base=None):
      self._base = thrift_base
      if self._base is not None and not inspect.isclass(self._base):
        raise ThriftRecordIO.InvalidThriftException(
          "ThriftCodec initialized with invalid Thrift base class")

    def encode(self, input):
      return _SER.serialize(input)

    def decode(self, input):
      if self._base is None:
        raise ThriftRecordIO.ThriftUnsuppliedException(
          "ThriftCodec cannot deserialize because no thrift_base supplied!")

      base = self._base()
      try:
        _SER.deserialize(base, input)
      except EOFError, e:
        raise RecordIO.PrematureEndOfStream(e)
      return base

class ThriftRecordReader(RecordIO.Reader):
  """
    RecordReader that deserializes Thrift objects instead of strings.
  """

  def __init__(self, fp, thrift_base):
    """
      Supplying a file pointer and Thrift class thrift_base, construct a
      ThriftRecordReader.

      May raise:
        RecordIO.ThriftUnavailableException if thrift deserialization is unavailable.
        RecordIO.ThriftUnsuppliedException if thrift argument not supplied
    """
    ThriftRecordIO.assert_has_thrift()
    if not thrift_base:
      raise ThriftRecordIO.ThriftUnsuppliedException(
        'Must construct ThriftRecordReader with valid thrift_base!')
    RecordIO.Reader.__init__(self, fp, ThriftRecordIO.ThriftCodec(thrift_base))

class ThriftRecordWriter(RecordIO.Writer):
  """
    RecordWriter that serializes Thrift objects instead of strings.
  """

  def __init__(self, fp):
    """
      Supplying a file pointer, construct a ThriftRecordWriter.

      May raise:
        RecordIO.ThriftUnavailableException if thrift deserialization is unavailable.
    """
    ThriftRecordIO.assert_has_thrift()
    RecordIO.Writer.__init__(self, fp, ThriftRecordIO.ThriftCodec())

  @staticmethod
  def append(filename, input, codec=ThriftRecordIO.ThriftCodec()):
    return RecordIO.Writer.append(filename, input, codec)
