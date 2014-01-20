import struct

from twitter.common.java.perfdata.attribute_buffer import SimpleAttributeBuffer
from twitter.common.java.perfdata.constants import TypeCode, Units, Variability


class PerfDataEntryHeader2(SimpleAttributeBuffer):
  """From v2_0/PerfDataBuffer.java:

   * typedef struct {
   *   jint entry_length;         // entry length in bytes
   *   jint name_offset;          // offset to entry name, relative to start
   *                              // of entry
   *   jint vector_length;        // length of the vector. If 0, then scalar.
   *   jbyte data_type;           // JNI field descriptor type
   *   jbyte flags;               // miscellaneous attribute flags
   *                              // 0x01 - supported
   *   jbyte data_units;          // unit of measure attribute
   *   jbyte data_variability;    // variability attribute
   *   jbyte data_offset;         // offset to data item, relative to start
   *                              // of entry.
   * } PerfDataEntry;
  """
  ATTRIBUTES = {
    'entry_length':  ('i', slice( 0,  4)),
    'name_offset':   ('i', slice( 4,  8)),
    'vector_length': ('i', slice( 8, 12)),
    'data_type':     ('b', slice(12, 13)),
    'flags':         ('b', slice(13, 14)),
    'data_units':    ('b', slice(14, 15)),
    'data_var':      ('b', slice(15, 16)),
    'data_offset':   ('i', slice(16, 20)),
  }
  LENGTH = 20


class PerfDataBuffer2Prologue(SimpleAttributeBuffer):
  ATTRIBUTES = {
    'accessible':      ('b', slice( 7,  8)),
    'prologue_used':   ('i', slice( 8, 12)),
    'overflow_offset': ('i', slice(12, 16)),
    'mtime':           ('q', slice(16, 24)),
    'entry_offset':    ('i', slice(24, 28)),
    'num_entries':     ('i', slice(28, 32))
  }


class PerfData2Format(object):
  def __init__(self, endianness=SimpleAttributeBuffer.LITTLE_ENDIAN):
    self._endianness = endianness

  def __call__(self, data):
    prologue = PerfDataBuffer2Prologue(data, self._endianness)

    if not prologue.accessible:
      return {}

    monitor_map = {}
    start_offset = prologue.entry_offset
    parsed_entries = 0

    def more_entries():
      return start_offset + PerfDataEntryHeader2.LENGTH < len(data)

    while more_entries() and parsed_entries < prologue.num_entries:
      entry = PerfDataEntryHeader2(
          data[start_offset:start_offset + PerfDataEntryHeader2.LENGTH], self._endianness)

      name_start = start_offset + entry.name_offset
      name_end = data.find('\x00', name_start)
      name = data[name_start:name_end]

      try:
        code = TypeCode.to_code(chr(entry.data_type))
      except KeyError:
        raise ValueError('Failed to figure out type of: %s' % name)
      variability = entry.data_var
      data_start = start_offset + entry.data_offset

      if entry.vector_length == 0:
        if code != TypeCode.LONG:
          raise ValueError('Unexpected monitor type: %d' % code)
        value = struct.unpack(
            '>q' if self._endianness is SimpleAttributeBuffer.BIG_ENDIAN else '<q',
            data[data_start:data_start + 8])[0]
        monitor_map[name] = (entry.data_units, value)
      else:
        if code != TypeCode.BYTE or entry.data_units != Units.STRING or (
            variability not in (Variability.CONSTANT, Variability.VARIABLE)):
          raise ValueError('Unexpected vector monitor: code:%s units:%s variability:%s' % (
              code, entry.data_units, variability))
        monitor_map[name] = (entry.data_units,
            data[data_start:data_start + entry.vector_length].rstrip('\r\n\x00'))

      start_offset += entry.entry_length
      parsed_entries += 1

    return self._postprocess(monitor_map)

  @classmethod
  def _postprocess(cls, monitor_map):
    """Given a monitor map of name = (unit, value), return a normalized set of
       counters."""
    frequency = 1.0 * monitor_map['sun.os.hrt.frequency'][1]

    def ticks_to_duration(value):
      return value / frequency

    def produce_value(unit, value):
      if unit == Units.TICKS:
        return ticks_to_duration(value)
      else:
        return value

    return dict((key, produce_value(value[0], value[1])) for key, value in monitor_map.items())
