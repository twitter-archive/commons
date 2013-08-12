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

import struct

import pytest

from twitter.common.recordio import RecordIO
from twitter.common.recordio import ThriftRecordWriter, ThriftRecordReader
from twitter.common.recordio.thrift_recordio import ThriftRecordIO
from twitter_test.thrift.ttypes import IntType, StringType, BinaryType

from recordio_test_harness import EphemeralFile


def test_basic_thriftrecordwriter_write():
  test_string = StringType("hello world")

  with EphemeralFile('w') as fp:
    fn = fp.name

    rw = ThriftRecordWriter(fp)
    rw.write(test_string)
    rw.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      assert rr.read() == test_string


def test_thriftrecordwriter_framing():
  test_string_1 = StringType("hello world")
  test_string_2 = StringType("ahoy ahoy, bonjour")

  with EphemeralFile('w') as fp:
    fn = fp.name

    rw = ThriftRecordWriter(fp)
    rw.write(test_string_1)
    rw.close()

    with open(fn, 'a') as fpa:
      rw = ThriftRecordWriter(fpa)
      rw.write(test_string_2)

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      assert rr.read() == test_string_1
      assert rr.read() == test_string_2


def test_thriftrecordreader_iteration():
  test_string_1 = StringType("hello world")
  test_string_2 = StringType("ahoy ahoy, bonjour")

  with EphemeralFile('w') as fp:
    fn = fp.name

    rw = ThriftRecordWriter(fp)
    rw.write(test_string_1)
    rw.write(test_string_2)
    rw.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      records = []
      for record in rr:
        records.append(record)
      assert records == [test_string_1, test_string_2]


def test_thriftrecordreader_nested_iteration():
  test_string_1 = StringType("hello world")
  test_string_2 = StringType("ahoy ahoy, bonjour")

  with EphemeralFile('w') as fp:
    fn = fp.name

    rw = ThriftRecordWriter(fp)
    rw.write(test_string_1)
    rw.write(test_string_2)
    rw.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      records = []
      for record in rr:
        records.append(record)
        for record2 in rr:
          records.append(record2)
      assert records == [
        test_string_1,
        test_string_1, test_string_2,
        test_string_2,
        test_string_1, test_string_2]


def test_paranoid_thrift_append_framing():
  test_string_1 = StringType("hello world")
  test_string_2 = StringType("ahoy ahoy, bonjour")

  with EphemeralFile('w') as fp:
    fn = fp.name

    ThriftRecordWriter.append(fn, test_string_1)
    ThriftRecordWriter.append(fn, test_string_2)

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      assert rr.read() == test_string_1
      assert rr.read() == test_string_2


def test_thrift_recordwriter_type_mismatch():
  test_string = StringType("hello world")
  with EphemeralFile('w') as fp:
    fn = fp.name

    rw = ThriftRecordWriter(fp)
    rw.write(test_string)
    rw.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, IntType)
      # This is a peculiar behavior of Thrift in that it just returns
      # ThriftType() with no serialization applied
      assert rr.read() == IntType()


def test_premature_end_of_stream_mid_message_thrift():
  with EphemeralFile('w') as fp:
    fn = fp.name

    fp.write(struct.pack('>L', 2))
    fp.write('a')
    fp.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()


def test_thrift_garbage():
  with EphemeralFile('w') as fp:
    fn = fp.name

    fp.write(struct.pack('>L', 2))
    fp.write('ab')
    fp.close()

    with open(fn) as fpr:
      rr = ThriftRecordReader(fpr, StringType)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()


def test_thrift_invalid_codec_with_nonclass():
  with EphemeralFile('w') as fp:
    with pytest.raises(ThriftRecordIO.InvalidThriftException):
      ThriftRecordReader(fp, 5)


def test_thrift_invalid_codec_with_object_instead_of_class():
  with EphemeralFile('w') as fp:
    with pytest.raises(ThriftRecordIO.InvalidThriftException):
      ThriftRecordReader(fp, StringType())
