from collections import deque
from datetime import datetime, timedelta
import errno
from io import BytesIO, FileIO
import os

from twitter.common.lang import Compatibility

from .parsers import Line


class Buffer(object):
  CHUNKSIZE = 65536

  @classmethod
  def maybe_filelike(cls, filename_or_filelike):
    if isinstance(filename_or_filelike, Compatibility.string):
      return FileIO(filename_or_filelike)
    else:
      return filename_or_filelike

  @classmethod
  def reset(cls, fp):
    try:
      fp.seek(0, os.SEEK_CUR)
    except IOError:
      return False
    except OSError as e:
      if e.errno == errno.ESPIPE:
        return False
      raise

  def __init__(self, filename_or_filelike, infinite=False):
    self._fp = self.maybe_filelike(filename_or_filelike)
    self._buffer = deque()
    self._tail = None
    self._infinite = infinite

  def next(self):
    last_chunk = self.CHUNKSIZE
    while len(self._buffer) == 0 and last_chunk == self.CHUNKSIZE:
      tail_add = self._fp.read(self.CHUNKSIZE)
      if tail_add:
        self._tail = self._tail + tail_add if self._tail else tail_add
        self._flatten_tail()
      last_chunk = len(tail_add)
    if self._buffer:
      return self._buffer.popleft()
    if last_chunk != self.CHUNKSIZE and self._infinite:
      self.reset(self._fp)
    if last_chunk != self.CHUNKSIZE and self._tail is not None and not self._infinite:
      rv = self._tail
      self._tail = None
      return rv

  def _flatten_tail(self):
    flattened = self._tail.split('\n')
    if len(flattened) > 1:
      self._buffer.extend(flattened[0:-1])
      self._tail = flattened[-1]


class Stream(object):
  class EOF(object): pass

  def __init__(self, filename_or_filelike, parsers, infinite=False):
    """
      Given a filelike-object and a set of Line-derived parsers (e.g. GlogLine, ZooLine),
      generate Line objects from the stream.  If infinite=True, continue after hitting
      EOF.
    """
    self._buffer = Buffer(filename_or_filelike, infinite)
    self._head = None
    self._tail = []
    self._infinite = infinite
    self._parsers = parsers

  def _full_head(self):
    return self._head.extend(self._tail) if self._tail else self._head

  def _handle_end(self):
    if self._infinite:
      return
    else:
      if self._head:
        rv = self._full_head()
        self._head, self._tail = None, []
        return rv
      else:
        return self.EOF

  def next(self):
    while True:
      line = self._buffer.next()
      if line is None:
        return self._handle_end()
      tail = Line.parse_order(line, *self._parsers)
      if tail is not None:
        if self._head:
          rv = self._full_head()
          self._head, self._tail = tail, []
          return rv
        else:
          self._head, self._tail = tail, []
      else:
        self._tail.append(line)


class StreamMuxer(object):
  """
    Multiplexes a set of streams into a single stream.
  """
  def __init__(self, streams):
    """
      Takes a set of (stream, label) pairs.
    """
    streams = list(streams)
    self._labels = dict(streams)
    self._refresh = set(stream for (stream, _) in streams)
    self._heads = set()

  def _collect(self):
    discard = set()
    for stream in self._refresh:
      line = stream.next()
      if line is Stream.EOF:
        discard.add(stream)
      elif line is not None:
        discard.add(stream)
        self._heads.add((line, stream))
    self._refresh -= discard

  def _pop(self):
    try:
      minimum = min(self._heads)
      self._heads.discard(minimum)
      return minimum
    except ValueError:
      pass

  def next(self):
    """
      Returns (label, Line) pairs as they're available, None if nothing is available or Stream.EOF
      if all streams have terminated.
    """
    self._collect()
    if not self._heads and not self._refresh:
      return Stream.EOF
    minimum = self._pop()
    if minimum:
      line, stream = minimum
      self._refresh.add(stream)
      return (self._labels[stream], line)
