from datetime import datetime, timedelta

from twitter.common.lang import total_ordering

# TODO(wickman) Do something that won't break if this is running over NYE?
_CURRENT_YEAR = str(datetime.now().year)


class Level(object):
  DEBUG   = 0
  INFO    = 10
  WARNING = 20
  ERROR   = 30
  FATAL   = 40


@total_ordering
class Line(object):
  __slots__ = (
    'raw',
    'level',
    'datetime',
    'pid',
    'source',
    'message'
  )

  @classmethod
  def parse(cls, line):
    """parses a line and returns Line if successfully parsed, ValueError/None otherwise."""
    raise NotImplementedError

  @staticmethod
  def parse_order(line, *line_parsers):
    """Given a text line and any number of Line implementations, return the first that matches
       or None if no lines match."""
    for parser in line_parsers:
      try:
        return parser.parse(line)
      except ValueError:
        continue

  def __init__(self, raw, level, dt, pid, source, message):
    (self.raw, self.level, self.datetime, self.pid, self.source, self.message) = (
        raw, level, dt, pid, source, message)

  def extend(self, lines):
    extension = '\n'.join(lines)
    return self.__class__('\n'.join([self.raw, extension]), self.level, self.datetime, self.pid,
        self.source, '\n'.join([self.message, extension]))

  def __lt__(self, other):
    return self.datetime < other.datetime

  def __gt__(self, other):
    return self.datetime > other.datetime

  def __eq__(self, other):
    if not isinstance(other, self.__class__):
      return False
    return (self.datetime == other.datetime and
            self.level == other.level and
            self.pid == other.pid and
            self.source == other.source and
            self.message == other.message)

  def __str__(self):
    return self.raw


class GlogLine(Line):
  LEVEL_MAP = {
    'I': Level.INFO,
    'W': Level.WARNING,
    'E': Level.ERROR,
    'F': Level.FATAL,
    'D': Level.DEBUG
  }

  @classmethod
  def split_time(cls, line):
    if len(line) == 0:
      raise ValueError
    if line[0] not in 'IWEFD':
      raise ValueError
    sline = line[1:].split(' ')
    if len(sline) < 2:
      raise ValueError
    t = datetime.strptime(''.join([_CURRENT_YEAR, sline[0], ' ', sline[1]]), '%Y%m%d %H:%M:%S.%f')
    return cls.LEVEL_MAP[line[0]], t, sline[2:]

  @classmethod
  def parse(cls, line):
    level, dt, rest = cls.split_time(line)
    pid, source, message = rest[0], rest[1], ' '.join(rest[2:])
    return cls(line, level, dt, pid, source, message)


class ZooLine(Line):
  LEVEL_MAP = {
    "ZOO_INVALID": 0,
    "ZOO_ERROR": Level.ERROR,
    "ZOO_WARN": Level.WARNING,
    "ZOO_INFO": Level.INFO,
    "ZOO_DEBUG": Level.DEBUG
  }

  @classmethod
  def parse(cls, line):
    sline = line.split(':')
    if len(sline) < 6:
      raise ValueError
    t = datetime.strptime(':'.join(sline[0:3]), '%Y-%m-%d %H:%M:%S,%f')
    pid = sline[3]
    ssource = sline[4].split('@')
    level = cls.LEVEL_MAP.get(ssource[0], 0)
    source = '@'.join(ssource[1:])
    return cls(line, level, t, pid, source, ':'.join(sline[5:]))
