# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

from twitter.common.lang import Compatibility
from twitter.common.log.parsers import GlogLine
from twitter.common.log.reader import (
  Buffer,
  Stream,
  StreamMuxer)


TEST_GLOG_LINES = """
Log file created at: 2012/11/01 18:39:49
Running on machine: smf1-aes-07-sr2.prod.twitter.com
[DIWEF]mmdd hh:mm:ss.uuuuuu pid file:line] msg
Command line: ./thermos_executor.pex
I1101 18:39:49.557605 14209 executor_base.py:43] Executor [None]: registered() called with:
I1101 18:39:49.558563 14209 executor_base.py:43] Executor [None]:    ExecutorInfo:  executor_id {
  value: "thermos-1351795185681-wickman-hello_world_crush-2-601439aa-6f17-4e78-90a0-ef40de4a36db"
}
resources {
  name: "cpus"
  type: SCALAR
  scalar {
    value: 0.25
  }
}
I1101 18:39:50.000000 14209 executor_base.py:43] Executor [None]:    Yet another
  set of
  executor
  lines
"""

TEST_GLOG_LINES_LENGTH = len(TEST_GLOG_LINES.split('\n'))


def sio():
  return Compatibility.StringIO(TEST_GLOG_LINES)


def read_all(buf, terminator=None):
  lines = []
  while True:
    line = buf.next()
    if line is terminator:
      break
    lines.append(line)
  return lines


def write_and_rewind(sio, buf):
  sio.write(buf)
  sio.seek(-len(buf), os.SEEK_CUR)


def test_buffer_tail():
  writer = Compatibility.StringIO()
  buf = Buffer(writer, infinite=False)  # infinite ==> eof is end of line
  assert buf.next() is None
  write_and_rewind(writer, '1234')
  assert buf.next() == '1234'

  writer = Compatibility.StringIO()
  buf = Buffer(writer, infinite=True)  # infinite ==> eof is end of line
  assert buf.next() is None
  write_and_rewind(writer, '1234')
  assert buf.next() is None
  write_and_rewind(writer, '\n')
  assert buf.next() == '1234'


def test_buffer_chunksizes():
  for bufsize in (1, 2, 3, 10, 32, 512, 1024, 4096):
    class TestBuffer(Buffer):
      CHUNKSIZE = bufsize
    buf = TestBuffer(sio())
    all_lines = read_all(buf)
    assert all_lines == TEST_GLOG_LINES.split('\n')
    assert len(all_lines) == TEST_GLOG_LINES_LENGTH
    assert '\n'.join(all_lines) == '\n'.join(TEST_GLOG_LINES.split('\n'))


def test_stream():
  stream = Stream(sio(), (GlogLine,))
  lines = read_all(stream, terminator=Stream.EOF)
  assert len(lines) == 3
  last_line = lines[-1]
  # does assembly of trailing non-GlogLines work properly?
  assert last_line.raw.startswith('I1101')
  assert TEST_GLOG_LINES[-len(last_line.raw):] == last_line.raw

  # test tailed logs
  writer = Compatibility.StringIO()
  stream = Stream(writer, (GlogLine,), infinite=True)
  assert stream.next() is None
  write_and_rewind(writer, lines[0].raw)
  assert stream.next() is None
  write_and_rewind(writer, '\n')

  # this is somewhat counterintuitive behavior -- we need to see two log lines in order
  # to print one, simply because otherwise we don't know if the current line is finished.
  # you could imagine a scenario, however, when you'd want (after a certain duration)
  # to print out whatever is in the buffers regardless.  this should probably be the
  # default behavior in infinite=True, but it will add a lot of complexity to the
  # implementation.
  assert stream.next() is None
  write_and_rewind(writer, lines[1].raw)
  assert stream.next() == lines[0]

  assert stream.next() is None
  write_and_rewind(writer, '\n')
  assert stream.next() == None
  write_and_rewind(writer, lines[2].raw)
  assert stream.next() == lines[1]

