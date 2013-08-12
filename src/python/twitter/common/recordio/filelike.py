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

"""Encapsulate a file-like object to provide a common interface for RecordIO streams"""

import errno
import os

from twitter.common import log


# TODO(wickman) This needs to be py 3.x friendly.
_VALID_STRINGIO_CLASSES = []

from StringIO import StringIO

# The hoops jumped through here are because StringI and StringO are not
# exposed directly in the stdlib.
_VALID_STRINGIO_CLASSES.append(StringIO)

try:
  from cStringIO import StringIO
  _VALID_STRINGIO_CLASSES.append(type(StringIO())) # cStringIO.StringI
  _VALID_STRINGIO_CLASSES.append(type(StringIO('foo'))) # cStringIO.StringO
except ImportError:
  pass

_VALID_STRINGIO_CLASSES = tuple(_VALID_STRINGIO_CLASSES)


class FileLike(object):
  class Error(Exception): pass

  @staticmethod
  def get(fp):
    if isinstance(fp, _VALID_STRINGIO_CLASSES):
      return StringIOFileLike(fp)
    elif isinstance(fp, file):
      return FileLike(fp)
    elif isinstance(fp, FileLike):
      return fp
    else:
      raise ValueError('Unknown file-like object %s' % fp)

  def __init__(self, fp):
    self._fp = fp

  @property
  def mode(self):
    return self._fp.mode

  def dup(self):
    fd = os.dup(self._fp.fileno())
    try:
      cur_fp = os.fdopen(fd, self._fp.mode)
      cur_fp.seek(0)
    except OSError as e:
      log.error('Failed to duplicate fd on %s, error = %s' % (self._fp.name, e))
      try:
        os.close(fd)
      except OSError as e:
        if e.errno != errno.EBADF:
          log.error('Failed to close duped fd on %s, error = %s' % (self._fp.name, e))
      raise self.Error('Failed to dup %s' % self._fp)
    return FileLike(cur_fp)

  def read(self, length):
    return self._fp.read(length)

  def write(self, data):
    return self._fp.write(data)

  def tell(self):
    return self._fp.tell()

  def seek(self, dest):
    return self._fp.seek(dest)

  def close(self):
    return self._fp.close()

  @property
  def name(self):
    return self._fp.name

  def flush(self):
    try:
      self._fp.flush()
      os.fsync(self._fp.fileno())
    except (IOError, OSError) as e:
      log.error("Failed to fsync on %s! Error: %s" % (self._fp.name, e))


class StringIOFileLike(FileLike):
  def __init__(self, fp):
    self._fp = fp

  @property
  def mode(self):
    return 'r+'

  @property
  def name(self):
    return 'FileLike'

  def flush(self):
    pass

  def dup(self):
    return StringIOFileLike(StringIO(self._fp.read()))
