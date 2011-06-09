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

import os
import tempfile
import struct

import pytest

from twitter.common.recordio import RecordIO
from twitter.common.recordio import RecordWriter, RecordReader

from recordio_test_harness import DurableFile, EphemeralFile

def test_raises_if_initialized_with_nil_filehandle():
  with pytest.raises(RecordIO.InvalidFileHandle):
    RecordWriter(None)
  with pytest.raises(RecordIO.InvalidFileHandle):
    RecordReader(None)

def test_recordwriter_raises_on_readonly_file():
  with EphemeralFile('r') as fp:
    with pytest.raises(RecordIO.InvalidFileHandle):
      RecordWriter(fp)

def test_raises_on_nonfile():
  with pytest.raises(RecordIO.InvalidFileHandle):
    RecordWriter('/tmp/poop')
  with pytest.raises(RecordIO.InvalidFileHandle):
    RecordReader('/tmp/poop')

def test_recordwriter_works_with_append():
  with EphemeralFile('a') as fp:
    try:
      RecordWriter(fp)
    except:
      assert False, 'Failed to initialize RecordWriter in append mode'

def test_recordwriter_works_with_readplus():
  with EphemeralFile('r+') as fp:
    try:
      RecordWriter(fp)
    except:
      assert False, 'Failed to initialize RecordWriter in r+ mode'

def test_recordwriter_works_with_write():
  with EphemeralFile('w') as fp:
    try:
      RecordWriter(fp)
    except:
      assert False, 'Failed to initialize RecordWriter in r+ mode'

def test_recordreader_works_with_plus():
  with EphemeralFile('a+') as fp:
    try:
      RecordReader(fp)
    except:
      assert False, 'Failed to initialize RecordWriter in r+ mode'
  with EphemeralFile('w+') as fp:
    try:
      RecordReader(fp)
    except:
      assert False, 'Failed to initialize RecordWriter in r+ mode'

def test_recordreader_fails_with_writeonly():
  with EphemeralFile('a') as fp:
    with pytest.raises(RecordIO.InvalidFileHandle):
      RecordReader(fp)
  with EphemeralFile('w') as fp:
    with pytest.raises(RecordIO.InvalidFileHandle):
      RecordReader(fp)

def test_paranoid_append_returns_false_on_nonexistent_file():
  fn = tempfile.mktemp()
  assert RecordWriter.append(fn, 'hello world!') == False

def test_basic_recordwriter_write():
  test_string = "hello world"
  with EphemeralFile('w') as fp:
    fn = fp.name
    rw = RecordWriter(fp)
    rw.write(test_string)
    rw.close()
    with open(fn) as fpr:
      rr = RecordReader(fpr)
      assert rr.read() == test_string

def test_basic_recordwriter_write_synced():
  test_string = "hello world"
  with EphemeralFile('w') as fp:
    fn = fp.name
    RecordWriter.do_write(fp, test_string, RecordIO.StringCodec(), sync=True)
    with open(fn) as fpr:
      rr = RecordReader(fpr)
      assert rr.read() == test_string

def test_recordwriter_framing():
  test_string_1 = "hello world"
  test_string_2 = "ahoy ahoy, bonjour"

  with EphemeralFile('w') as fp:
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

def test_paranoid_append_framing():
  with DurableFile('w') as fp:
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

def test_basic_recordreader_try_read():
  test_string = "hello world"
  with EphemeralFile('r') as fp:
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

def test_basic_recordreader_read():
  test_string = "hello world"
  with EphemeralFile('r') as fp:
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

def test_premature_end_of_stream():
  with EphemeralFile('w') as fp:
    fn = fp.name

    fp.write(struct.pack('>L', 1))
    fp.close()

    with open(fn) as fpr:
      rr = RecordReader(fpr)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()

def test_premature_end_of_stream_mid_message():
  with EphemeralFile('w') as fp:
    fn = fp.name

    fp.write(struct.pack('>L', 2))
    fp.write('a')
    fp.close()

    with open(fn) as fpr:
      rr = RecordReader(fpr)
      with pytest.raises(RecordIO.PrematureEndOfStream):
        rr.read()

def test_sanity_check_bytes():
  with EphemeralFile('w') as fp:
    fn = fp.name

    fp.write(struct.pack('>L', RecordIO.SANITY_CHECK_BYTES+1))
    fp.write('a')
    fp.close()

    with open(fn) as fpr:
      rr = RecordReader(fpr)
      with pytest.raises(RecordIO.RecordSizeExceeded):
        rr.read()
