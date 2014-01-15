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

try:
  from cStringIO import StringIO
except ImportError:
  from StringIO import StringIO

from contextlib import contextmanager
import mox
import os
import struct
import tempfile

from twitter.common.recordio import RecordIO, RecordWriter, RecordReader, StringCodec
from twitter.common.recordio.filelike import FileLike, StringIOFileLike

import pytest

from recordio_test_harness import (
    DurableFile as DurableFileBase,
    EphemeralFile as EphemeralFileBase
)


class RecordioTestBase(mox.MoxTestBase):
  @classmethod
  @contextmanager
  def DurableFile(cls, mode):
    with DurableFileBase(mode) as fp:
      yield fp

  @classmethod
  @contextmanager
  def EphemeralFile(cls, mode):
    with EphemeralFileBase(mode) as fp:
      yield fp

  def test_append_fails_on_nonexistent_file(self):
    fn = tempfile.mktemp()
    assert RecordWriter.append(fn, 'hello world!') == False

  def test_append_fails_on_inaccessible_file(self):
    with RecordioTestBase.EphemeralFile('w') as fp:
      os.fchmod(fp.fileno(), 000)
      with pytest.raises(IOError):
        RecordWriter.append(fp.name, 'hello world!')

  def test_append_raises_on_bad_codec(self):
    fn = tempfile.mktemp()
    with pytest.raises(RecordIO.InvalidCodec):
      RecordIO.Writer.append(fn, 'hello world!', 'not a codec!')

  def test_append_fails_on_errors(self):
    record = 'hello'
    self.mox.StubOutWithMock(RecordIO.Writer, 'do_write')
    RecordIO.Writer.do_write(mox.IsA(file), record, mox.IsA(StringCodec)).AndRaise(IOError)
    RecordIO.Writer.do_write(mox.IsA(file), record, mox.IsA(StringCodec)).AndRaise(OSError)

    self.mox.ReplayAll()

    with RecordioTestBase.EphemeralFile('r+') as fp:
      assert RecordIO.Writer.append(fp.name, record, StringCodec()) == False
      assert RecordIO.Writer.append(fp.name, record, StringCodec()) == False

  def test_basic_recordwriter_write(self):
    test_string = "hello world"
    with self.EphemeralFile('r+') as fp:
      rw = RecordWriter(fp)
      rw.write(test_string)
      fp.seek(0)
      rr = RecordReader(fp)
      assert rr.read() == test_string

  def test_basic_recordwriter_write_synced(self):
    test_string = "hello world"
    with self.EphemeralFile('r+') as fp:
      RecordWriter.do_write(fp, test_string, StringCodec(), sync=True)
      fp.seek(0)
      rr = RecordReader(fp)
      assert rr.read() == test_string

  def test_basic_recordwriter_write_fail(self):
    test_string = "hello"
    header = struct.pack('>L', len(test_string))
    fp = self.mox.CreateMock(file)
    fp.write(header).AndRaise(IOError)
    fp.write(header).AndRaise(OSError)

    self.mox.ReplayAll()

    assert RecordWriter.do_write(fp, test_string, StringCodec()) == False
    assert RecordWriter.do_write(fp, test_string, StringCodec()) == False

  def test_basic_recordreader_iterator(self):
    test_strings = ["hello", "world", "etc"]
    with self.EphemeralFile('r+') as fp:
      for string in test_strings:
        RecordWriter.do_write(fp, string, StringCodec(), sync=True)
      fp.seek(0)
      rr = RecordReader(fp)
      assert list(rr) == test_strings

  def test_basic_recordreader_iter_failure(self):
    self.mox.StubOutWithMock(RecordIO.Reader, 'do_read')
    fp = self.mox.CreateMock(FileLike)
    fp.mode = 'r+'
    fp.dup().AndReturn(fp)
    RecordIO.Reader.do_read(fp, mox.IsA(StringCodec)).AndRaise(RecordIO.Error)
    fp.close()

    self.mox.ReplayAll()

    with pytest.raises(RecordIO.Error):
      list(RecordReader(fp))

  def test_basic_recordreader_dup_failure(self):
    fp = self.mox.CreateMock(FileLike)
    fp.mode = 'r+'
    fp.Error = FileLike.Error
    fp.dup().AndRaise(FileLike.Error)

    self.mox.ReplayAll()

    rr = RecordReader(fp)
    assert list(rr) == []
    self.mox.VerifyAll()

  def test_bad_header_size(self):
    with self.EphemeralFile('r+') as fp:
      fpw = FileLike.get(fp)
      fpw.write(struct.pack('>L', RecordIO.MAXIMUM_RECORD_SIZE))
      fpw._fp.truncate(RecordIO.RECORD_HEADER_SIZE - 1)
      fpw.flush()
      fpw.seek(0)

      rr = RecordReader(fp)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()
      assert fpw.tell() != 0
      fpw.seek(0)
      assert rr.try_read() is None
      assert fpw.tell() == 0

  def test_record_too_large(self):
    with self.EphemeralFile('r+') as fp:
      fpw = FileLike.get(fp)
      fpw.write(struct.pack('>L', RecordIO.MAXIMUM_RECORD_SIZE+1))
      fpw.write('a')
      fpw.flush()
      fpw.seek(0)

      rr = RecordReader(fp)
      with pytest.raises(RecordIO.RecordSizeExceeded):
        rr.read()

  def test_raises_if_initialized_with_nil_filehandle(self):
    with pytest.raises(RecordIO.InvalidFileHandle):
      RecordWriter(None)
    with pytest.raises(RecordIO.InvalidFileHandle):
      RecordReader(None)

  def test_raises_if_initialized_with_bad_codec(self):
    with self.EphemeralFile('r+') as fp:
      with pytest.raises(RecordIO.InvalidCodec):
        RecordIO.Writer(fp, "not_a_codec")

  def test_premature_end_of_stream(self):
    with self.EphemeralFile('r+') as fp:
      fpr = FileLike.get(fp)
      fpr = fp
      fpr.write(struct.pack('>L', 1))
      fpr.seek(0)
      rr = RecordReader(fpr)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()

  def test_premature_end_of_stream_mid_message(self):
    with self.EphemeralFile('r+') as fp:
      fpr = FileLike.get(fp)
      fpr = fp
      fpr.write(struct.pack('>L', 2))
      fpr.write('a')
      fpr.seek(0)
      rr = RecordReader(fpr)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()

  def test_filelike_dup_raises(self):
    self.mox.StubOutWithMock(os, 'fdopen')
    self.mox.StubOutWithMock(os, 'close')
    os.fdopen(mox.IsA(int), mox.IsA(str)).AndRaise(OSError)
    os.close(mox.IsA(int)).AndRaise(OSError)

    self.mox.ReplayAll()

    with RecordioTestBase.EphemeralFile('r+') as fp:
      fl = FileLike(fp)
      with pytest.raises(FileLike.Error):
        fl.dup()

  def test_basic_recordwriter_write_synced_raises(self):
    test_string = "hello world"
    self.mox.StubOutWithMock(os, 'fsync')
    with RecordioTestBase.EphemeralFile('r+') as fp:
      os.fsync(fp.fileno()).AndRaise(OSError)

      self.mox.ReplayAll()

      rw = RecordWriter(FileLike(fp))
      rw.set_sync(True)
      rw.write(test_string)
      fp.seek(0)
      rr = RecordReader(fp)
      assert rr.read() == test_string


class TestRecordioBuiltin(RecordioTestBase):
  def test_recordwriter_framing(self):
    test_string_1 = "hello world"
    test_string_2 = "ahoy ahoy, bonjour"

    with self.EphemeralFile('w') as fp:
      fn = fp.name
      rw = RecordWriter(fp)
      rw.write(test_string_1)
      rw.close()

      with open(fn, 'a') as fpa:
        rw = RecordWriter(fpa)
        rw.write(test_string_2)

      with open(fn) as fpr:
        rr = RecordReader(fpr)
        assert rr.read() == test_string_1
        assert rr.read() == test_string_2

  def test_paranoid_append_framing(self):
    with self.DurableFile('w') as fp:
      fn = fp.name

    test_string_1 = "hello world"
    test_string_2 = "ahoy ahoy, bonjour"

    RecordWriter.append(fn, test_string_1)
    RecordWriter.append(fn, test_string_2)

    with open(fn) as fpr:
      rr = RecordReader(fpr)
      assert rr.read() == test_string_1
      assert rr.read() == test_string_2

    os.remove(fn)

  def test_recordwriter_raises_on_readonly_file(self):
    with self.EphemeralFile('r') as fp:
      with pytest.raises(RecordIO.InvalidFileHandle):
        RecordWriter(fp)

  def test_recordwriter_initializing(self):
    for mode in ('a', 'r+', 'w'):
      with self.EphemeralFile(mode) as fp:
        try:
          RecordWriter(fp)
        except Exception as e:
          assert False, (
              "Failed to initialize RecordWriter in '%s' mode (exception: %s)" % (mode, e))

  def test_recordreader_works_with_plus(self):
    for mode in ('a+', 'w+'):
      with self.EphemeralFile(mode) as fp:
        try:
          RecordReader(fp)
        except Exception as e:
          assert False, (
              "Failed to initialize RecordReader in '%s' mode (exception: %s)" % (mode, e))

  def test_recordreader_fails_with_writeonly(self):
    for mode in ('a', 'w'):
      with self.EphemeralFile(mode) as fp:
        with pytest.raises(RecordIO.InvalidFileHandle):
          RecordReader(fp)

  def test_basic_recordreader_try_read(self):
    test_string = "hello world"
    with self.EphemeralFile('r') as fp:
      fn = fp.name

      rr = RecordReader(fp)
      assert rr.try_read() is None
      rr.close()

      with open(fn, 'w') as fpw:
        rw = RecordWriter(fpw)
        rw.write(test_string)

      with open(fn) as fpr:
        rr = RecordReader(fpr)
        assert rr.try_read() == test_string

  def test_basic_recordreader_read(self):
    test_string = "hello world"
    with self.EphemeralFile('r') as fp:
      fn = fp.name

      rr = RecordReader(fp)
      assert rr.read() is None
      rr.close()

      with open(fn, 'w') as fpw:
        rw = RecordWriter(fpw)
        rw.write(test_string)

      with open(fn) as fpr:
        rr = RecordReader(fpr)
        assert rr.read() == test_string


class TestRecordioStringIO(RecordioTestBase):
  @classmethod
  @contextmanager
  def EphemeralFile(cls, mode):
    yield StringIO()

  def test_string_codec(self):
    for bad_value in (None, 1234, object):
      with pytest.raises(RecordIO.InvalidTypeException):
        assert StringCodec().encode(bad_value)
      with pytest.raises(RecordIO.InvalidTypeException):
        assert StringCodec().decode(bad_value)
