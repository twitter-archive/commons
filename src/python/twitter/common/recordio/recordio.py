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
import types
import struct
import errno

from twitter.common import log

class RecordIO(object):
  class PrematureEndOfStream(Exception): pass
  class RecordSizeExceeded(Exception): pass
  class InvalidTypeException(Exception): pass
  class InvalidFileHandle(Exception): pass
  class InvalidArgument(Exception): pass
  class UnimplementedException(Exception): pass
  class InvalidCodec(Exception): pass

  # Maximum record size
  SANITY_CHECK_BYTES = 64 * 1024 * 1024


  class Codec(object):
    """
      An encoder/decoder interface for bespoke RecordReader/Writers.
    """
    def __init__(self):
      pass

    def encode(self, blob):
      """
        Given: blob in custom format
        Return: serialized byte data
      """
      raise RecordIO.UnimplementedException("Codec.encode pure virtual.")

    def decode(self, blob):
      """
        Given: deserialized byte data
        Return: blob in custom format
      """
      raise RecordIO.UnimplementedException("Codec.decode pure virtual.")


  class StringCodec(Codec):
    def __init__(self):
      RecordIO.Codec.__init__(self)

    @staticmethod
    def code(blob):
      if blob is None: return None
      if not isinstance(blob, types.StringType):
        raise RecordIO.InvalidTypeException("blob (type=%s) not StringType!" % type(blob))
      return blob

    def encode(self, blob):
      return RecordIO.StringCodec.code(blob)

    def decode(self, blob):
      return RecordIO.StringCodec.code(blob)

  class Stream(object):
    def __init__(self, fp, codec):
      def validate_filehandle():
        if fp is None:
          raise RecordIO.InvalidFileHandle(
            'Intialized with an invalid file handle: %s' % fp)
        if not isinstance(fp, types.FileType):
          raise RecordIO.InvalidFileHandle(
            'RecordWriter initialized with something other than filehandle! fp=%s' % fp)

      def validate_codec():
        if not isinstance(codec, RecordIO.Codec):
          raise RecordIO.InvalidTypeException("Expected codec to be subclass of RecordIO.Codec")

      validate_filehandle()
      validate_codec()
      self._fp = fp
      self._codec = codec

    def close(self):
      """
        Close the underlying filehandle of the RecordIO stream.
      """
      self._fp.close()

  class Reader(Stream):
    def __init__(self, fp, codec):
      """
        Initialize a Reader from the file pointer fp.
      """
      RecordIO.Stream.__init__(self, fp, codec)
      if ('w' in self._fp.mode or 'a' in self._fp.mode) and '+' not in self._fp.mode:
        raise RecordIO.InvalidFileHandle(
          'Filehandle supplied to RecordReader does not appear to be readable!')

    def __iter__(self):
      """
        May raise:
          RecordIO.PrematureEndOfStream
      """
      fd = os.dup(self._fp.fileno())
      try:
        cur_fp = os.fdopen(fd, self._fp.mode)
        cur_fp.seek(0)
      except OSError, e:
        log.error('Failed to duplicate fd on %s, error = %s' % (self._fp.name, e))
        try:
          os.close(fd)
        except OSError, e:
          if e.errno != errno.EBADF:
            log.error('Failed to close duped fd on %s, error = %s' % (self._fp.name, e))
        return

      try:
        while True:
          blob = RecordIO.Reader.do_read(cur_fp, self._codec)
          if blob:
            yield blob
          else:
            break
      finally:
        cur_fp.close()

    @staticmethod
    def do_read(fp, decoder):
      blob = fp.read(4)
      if len(blob) == 0:
        log.debug("%s has no data (cur offset = %d)" % (fp.name, fp.tell()))
        # Reset EOF
        # TODO(wickman)  Should we also do this in the PrematureEndOfStream case before
        # raising, or rely upon the exception handler to take care of that for us?
        fp.seek(fp.tell())
        return None
      if len(blob) != 4:
        raise RecordIO.PrematureEndOfStream("Expected 4 bytes, got %d" % len(blob))
      blob_len = struct.unpack('>L', blob)[0]
      if blob_len > RecordIO.SANITY_CHECK_BYTES:
        raise RecordIO.RecordSizeExceeded()

      # read frame
      # -----
      read_blob = fp.read(blob_len)
      if len(read_blob) != blob_len:
        raise RecordIO.PrematureEndOfStream()
      return decoder.decode(read_blob)

    def read(self):
      """
        Read a single record from this stream.  Updates the file position on both
        success and failure (unless no data is available, in which case the file
        position is unchanged and None is returned.)

        Returns string blob or None if no data available.

        May raise:
          RecordIO.PrematureEndOfStream if the stream is truncated in the middle of
            and expected message
          RecordIO.RecordSizeExceeded if the message exceeds RecordIO.SANITY_CHECK_BYTES
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
      read_blob = None
      try:
        read_blob = self.read()
      except RecordIO.PrematureEndOfStream, e:
        log.debug('Got premature end of stream [%s], skipping - %s' % (self._fp.name, e))
        self._fp.seek(pos)
        return None
      return read_blob

  class Writer(Stream):
    def __init__(self, fp, codec, sync=False):
      """
        Initialize a Writer from the file pointer fp.

        If sync=True is supplied, then all mutations are fsynced after write, otherwise
        standard filesystem buffering is employed.
      """
      RecordIO.Stream.__init__(self, fp, codec)
      if 'w' not in self._fp.mode and 'a' not in self._fp.mode and '+' not in self._fp.mode:
        raise RecordIO.InvalidFileHandle(
          'Filehandle supplied to RecordWriter does not appear to be writeable!')
      self.set_sync(sync)

    def set_sync(self, value):
      self._sync = bool(value)

    @staticmethod
    def _fsync(fp):
      try:
        fp.flush()
        os.fsync(fp.fileno())
      except:
        log.error("Failed to fsync on %s!  Continuing..." % fp.name)

    @staticmethod
    def do_write(fp, input, codec, sync=False):
      """
        Write a record to the current fp using the supplied codec.

        Returns True on success, False on any filesystem failure.

        May raise:
          RecordIO.UnknownTypeException if blob is not a string.
      """
      blob = codec.encode(input)
      blob_len = len(blob)
      try:
        fp.write(struct.pack(">L", blob_len))
        fp.write(blob)
      except Exception, e:
        log.debug("Got exception in write(%s): %s" % (fp.name, e))
        return False
      if sync:
        RecordIO.Writer._fsync(fp)
      return True

    @staticmethod
    def append(filename, input, codec):
      """
        Given a filename stored in RecordIO format, open the file, append a
        blob to it and close.

        Returns True if it succeeds, or False if it fails for any reason.
      """
      rv = False
      if not isinstance(codec, RecordIO.Codec):
        raise RecordIO.InvalidCodec("append called with an invalid codec!")
      if not os.path.exists(filename):
        return False
      try:
        with open(filename, "a+") as fp:
          rv = RecordIO.Writer.do_write(fp, input, codec)
      except Exception, e:
        if fp:
          log.debug("Unexpected exception (%s), but continuing" % e)
        else:
          raise e
      return rv

    def write(self, blob):
      """
        Append the blob to the current RecordWriter.

        Returns True on success, False on any filesystem failure.

        May raise:
          RecordIO.UnknownTypeException if blob is not a string.
      """
      return RecordIO.Writer.do_write(self._fp, blob, self._codec, sync=self._sync)

class RecordWriter(RecordIO.Writer):
  """
    Write framed string records to a stream.

    Max record size is 64MB for the sake of sanity.
  """
  def __init__(self, fp):
    RecordIO.Writer.__init__(self, fp, RecordIO.StringCodec())

  @staticmethod
  def append(filename, blob, codec=RecordIO.StringCodec()):
    return RecordIO.Writer.append(filename, blob, codec)

class RecordReader(RecordIO.Reader):
  """
    Read framed string record from a RecordWriter stream.
  """
  def __init__(self, fp):
    RecordIO.Reader.__init__(self, fp, RecordIO.StringCodec())
