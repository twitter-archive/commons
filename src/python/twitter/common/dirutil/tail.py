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

__author__ = 'Brian Wickman'

import os
import errno
import time


def _tail_lines(fd, linesback=10):
  if fd is None:
    return

  # Contributed to Python Cookbook by Ed Pascoe (2003)
  avgcharsperline = 75

  while True:
    try:
      fd.seek(int(-1 * avgcharsperline * linesback), 2)
    except IOError:
      fd.seek(0)

    atstart = fd.tell() == 0

    lines = fd.read().splitlines()
    if atstart or len(lines) > (linesback + 1):
      break

    avgcharsperline = avgcharsperline * 1.3

  if len(lines) > linesback:
    start = len(lines) - linesback - 1
  else:
    start = 0

  return lines[start:start+linesback]


def wait_until_opened(filename, forever=True, clock=time):
  while True:
    try:
      return open(filename, 'r')
    except OSError as e:
      if e.errno == errno.ENOENT:
        if forever:
          clock.sleep(1)
        else:
          return None
      else:
        raise


def tail(filename, lines=10):
  with open(filename, 'r') as fp:
    for line in _tail_lines(fp, lines):
      yield line


def tail_f(filename, forever=True, include_last=False, clock=time):
  fd = wait_until_opened(filename, forever, clock)

  # wind back to near the end of the file...
  last_lines = _tail_lines(fd, 10)

  while True:
    if fd is None:
      return

    where = fd.tell()

    if last_lines:
      yield last_lines.pop(0)
      continue
    else:
      line = fd.readline()

    if line:
      yield line
    else:
      # check health of the file descriptor.
      fd_results = os.fstat(fd.fileno())
      try:
        st_results = None
        st_results = os.stat(filename)
      except OSError as e:
        if e.errno == errno.ENOENT:
          fd = wait_until_opened(filename, forever, clock)
          continue
        else:
          raise

      # file changed from underneath us, reopen
      if fd_results.st_ino != st_results.st_ino:
        fd.close()
        fd = wait_until_opened(filename, forever, clock)
        continue

      if st_results.st_size < where:
        # file truncated, rewind
        fd.seek(0)
      else:
        # our buffer has not yet caught up, wait.
        clock.sleep(1)
        fd.seek(where)
