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

"""Generic interfaces for record-based IO streams.

This module contains a RecordIO specification which defines interfaces for reading and writing to
sequential record streams using codecs. A record consists of a header (containing the length of the
frame), and a frame containing the encoded data.

To create a new RecordIO implementation, subclass RecordReader, RecordWriter, and Codec.
A basic example, StringRecordIO, is provided.

"""

import errno
from abc import abstractmethod
import os
import struct

from twitter.common import log
from twitter.common.lang import Compatibility, Interface

from .filelike import FileLike


class RecordIO(object):
  class Error(Exception): pass
  class PrematureEndOfStream(Error): pass
  class RecordSizeExceeded(Error): pass
  class InvalidTypeException(Error): pass
  class InvalidFileHandle(Error): pass
  class InvalidArgument(Error): pass
  class InvalidCodec(Error): pass

  RECORD_HEADER_SIZE = 4
  MAXIMUM_RECORD_SIZE = 64 * 1024 * 1024

  class Codec(Interface):
    """
      An encoder/decoder interface for bespoke RecordReader/Writers.
    """
    @abstractmethod
    def encode(self, blob):
      """
        Given: blob in custom format
        Return: serialized byte data
        Raises: InvalidTypeException if a bad blob type is supplied
      """

    @abstractmethod
    def decode(self, blob):
      """
        Given: deserialized byte data
        Return: blob in custom format
        Raises: InvalidTypeException if a bad blob type is supplied
      """

  class _Stream(object):
    """
      Shared initialization functionality for Reader/Writer
    """
    def __init__(self, fp, codec):
      try:
        self._fp = FileLike.get(fp)
      except ValueError as err:
        raise RecordIO.InvalidFileHandle(err)
      if not isinstance(codec, RecordIO.Codec):
        raise RecordIO.InvalidCodec("Codec must be subclass of RecordIO.Codec")
      self._codec = codec

    def close(self):
      """
        Close the underlying filehandle of the RecordIO stream.
      """
      self._fp.close()

  class Reader(_Stream):
    def __init__(self, fp, codec):
      """
        Initialize a Reader from file-like fp, with RecordIO.Codec codec
      """
      RecordIO._Stream.__init__(self, fp, codec)
      if ('w' in self._fp.mode or 'a' in self._fp.mode) and '+' not in self._fp.mode:
        raise RecordIO.InvalidFileHandle(
          'Filehandle supplied to RecordReader does not appear to be readable!')

    def __iter__(self):
      """
      Return an iterator over the entire contents of the underlying file handle.

        May raise:
          RecordIO.Error or subclasses
      """
      try:
        dup_fp = self._fp.dup()
      except self._fp.Error:
        log.error('Failed to dup %r' % self._fp)
        return

      try:
        while True:
          blob = RecordIO.Reader.do_read(dup_fp, self._codec)
          if blob:
            yield blob
          else:
            break
      finally:
        dup_fp.close()

    @staticmethod
    def do_read(fp, decoder):
      """
        Read a single record from the given filehandle and decode using the supplied decoder.

        May raise:
          RecordIO.PrematureEndOfStream if the stream is truncated in the middle of
            an expected message
          RecordIO.RecordSizeExceeded if the message exceeds RecordIO.MAXIMUM_RECORD_SIZE

      """
      # read header
      header = fp.read(RecordIO.RECORD_HEADER_SIZE)
      if len(header) == 0:
        log.debug("%s has no data (current offset = %d)" % (fp.name, fp.tell()))
        # Reset EOF (appears to be only necessary on OS X)
        fp.seek(fp.tell())
        return None
      elif len(header) != RecordIO.RECORD_HEADER_SIZE:
        raise RecordIO.PrematureEndOfStream(
            "Expected %d bytes in header, got %d" % (RecordIO.RECORD_HEADER_SIZE, len(header)))
      blob_len = struct.unpack('>L', header)[0]
      if blob_len > RecordIO.MAXIMUM_RECORD_SIZE:
        raise RecordIO.RecordSizeExceeded("Record exceeds maximum allowable size")

      # read frame
      read_blob = fp.read(blob_len)
      if len(read_blob) != blob_len:
        raise RecordIO.PrematureEndOfStream(
          'Expected %d bytes in frame, got %d' % (blob_len, len(read_blob)))
      return decoder.decode(read_blob)

    def read(self):
      """
        Read a single record from this stream.  Updates the file position on both
        success and failure (unless no data is available, in which case the file
        position is unchanged and None is returned.)

        Returns string blob or None if no data available.

        May raise:
          RecordIO.PrematureEndOfStream if the stream is truncated in the middle of
            an expected message
          RecordIO.RecordSizeExceeded if the message exceeds RecordIO.MAXIMUM_RECORD_SIZE
      """
      return RecordIO.Reader.do_read(self._fp, self._codec)

    def try_read(self):
      """
        Attempt to read a single record from the stream.  Only updates the file position
        if a read was successful.

        Returns string blob or None if no data available.

        May raise:
          RecordIO.RecordSizeExceeded
      """
      pos = self._fp.tell()
      try:
        return self.read()
      except RecordIO.PrematureEndOfStream as e:
        log.debug('Got premature end of stream [%s], skipping - %s' % (self._fp.name, e))
        self._fp.seek(pos)
        return None

  class Writer(_Stream):
    def __init__(self, fp, codec, sync=False):
      """
        Initialize a Writer from the FileLike fp, with RecordIO.Codec codec.

        If sync=True is supplied, then all mutations are fsynced after write, otherwise
        standard filesystem buffering is employed.
      """
      RecordIO._Stream.__init__(self, fp, codec)
      if 'w' not in self._fp.mode and 'a' not in self._fp.mode and '+' not in self._fp.mode:
        raise RecordIO.InvalidFileHandle(
          'Filehandle supplied to RecordWriter does not appear to be writeable!')
      self.set_sync(sync)

    def set_sync(self, value):
      self._sync = bool(value)

    @staticmethod
    def do_write(fp, record, codec, sync=False):
      """
        Write a record to the specified fp using the supplied codec.

        Returns True on success, False on any filesystem failure.
      """
      blob = codec.encode(record)
      header = struct.pack(">L", len(blob))
      try:
        fp.write(header)
        fp.write(blob)
      except (IOError, OSError) as e:
        log.debug("Got exception in write(%s): %s" % (fp.name, e))
        return False
      if sync:
        fp.flush()
      return True

    @staticmethod
    def append(filename, record, codec):
      """
        Given a filename stored in RecordIO format, open the file, append a
        record to it and close.

        Returns True if it succeeds, or False if it fails for any reason.
        Raises IOError, OSError if there is a problem opening filename for appending.
      """
      if not isinstance(codec, RecordIO.Codec):
        raise RecordIO.InvalidCodec("append called with an invalid codec!")
      if not os.path.exists(filename):
        return False
      try:
        fp = None
        with open(filename, "a+") as fp:
          return RecordIO.Writer.do_write(fp, record, codec)
      except (IOError, OSError) as e:
        if fp:
          log.debug("Unexpected exception (%s), but continuing" % e)
          return False
        else:
          raise

    def write(self, blob):
      """
        Append the blob to the current RecordWriter.

        Returns True on success, False on any filesystem failure.
      """
      return RecordIO.Writer.do_write(self._fp, blob, self._codec, sync=self._sync)


class StringCodec(RecordIO.Codec):
  """
    A simple string-based implementation of Codec.

    Performs no actual encoding/decoding; simply verifies that input is a string
  """
  @staticmethod
  def _validate(blob):
    if not isinstance(blob, Compatibility.string):
      raise RecordIO.InvalidTypeException("blob (type=%s) not StringType!" % type(blob))
    return blob

  def encode(self, blob):
    return self._validate(blob)

  def decode(self, blob):
    return self._validate(blob)


class StringRecordReader(RecordIO.Reader):
  """
    Simple RecordReader that deserializes strings.
  """
  def __init__(self, fp):
    RecordIO.Reader.__init__(self, fp, StringCodec())


class StringRecordWriter(RecordIO.Writer):
  """
    Write framed string records to a stream.

    Max record size is 64MB for the sake of sanity.
  """
  def __init__(self, fp):
    RecordIO.Writer.__init__(self, fp, StringCodec())

  @staticmethod
  def append(filename, blob, codec=StringCodec()):
    return RecordIO.Writer.append(filename, blob, codec)


RecordReader = StringRecordReader
RecordWriter = StringRecordWriter
