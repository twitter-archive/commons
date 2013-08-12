from abc import abstractmethod

from twitter.common.lang import Interface
from twitter.common.string import ScanfParser

class ProcessHandle(Interface):
  """
    ProcessHandle interface.  Methods that must be exposed by whatever process
    monitoring mechanism you use.
  """
  @abstractmethod
  def cpu_time(self):
    """
      Total cpu time of this process.
    """

  @abstractmethod
  def wall_time(self):
    """
      Total wall time this process has been up.
    """

  @abstractmethod
  def pid(self):
    """
      PID of the process.
    """

  @abstractmethod
  def ppid(self):
    """
      Parent PID of the process.
    """

  @abstractmethod
  def user(self):
    """
      The owner of the process.
    """

  @abstractmethod
  def cwd(self):
    """
      The current working directory of the process.
    """

  @abstractmethod
  def cmdline(self):
    """
      The full command line of the process.
    """
    raise NotImplementedError



class ProcessHandleParser(ScanfParser):
  """
    Given:
      attrs: list of attribute names
      type_map: map of attribute name to attribute type (%d/%u/etc format converter)
      handlers: optional set of postprocessing callbacks that take (attribute, value)
    Process a line from one of the process information sources (e.g. ps, procfs.)
  """
  def parse(self, line):
    d = {}
    try:
      so = ScanfParser.parse(self, ' '.join(line.split()), True)
      for attr, value in zip(self._attrs, so.ungrouped()):
        d[attr] = self._handlers[attr](attr, value) if attr in self._handlers else value
    except ScanfParser.ParseError as e:
      return {}
    return d

  def __init__(self, attrs, type_map, handlers = {}):
    self._attrs = attrs
    self._handlers = handlers
    attr_list = map(type_map.get, attrs)
    ScanfParser.__init__(self, ' '.join(attr_list))


class ProcessHandleParserBase(object):
  """
    Given a provider of process lines, parse them into bundles of attributes that can be
    translated into ProcessHandles.
  """
  def _produce(self):
    raise NotImplementedError

  def _realize(self):
    return self._realize_from_line(self._produce())

  def _realize_from_line(self, line):
    self._exists = False
    if line is None:
      self._attrs = {}
    else:
      self._attrs = self.PARSER.parse(line)
      if self._attrs:
        self._pid = self._attrs['pid']
        self._exists = True

  @classmethod
  def from_line(cls, line):
    proc = cls()
    proc._realize_from_line(line)
    return proc

  @classmethod
  def init_class(cls):
    if not hasattr(cls, 'PARSER'):
      assert hasattr(cls, 'ATTRS')
      assert hasattr(cls, 'TYPE_MAP')
      assert hasattr(cls, 'HANDLERS')
      setattr(cls, 'PARSER', ProcessHandleParser(cls.ATTRS, cls.TYPE_MAP, cls.HANDLERS))
    if not hasattr(cls, 'ALIASES'):
      cls.ALIASES = {}

  def __init__(self, pid=-1):
    self.init_class()
    self._exists = False
    self._pid = pid
    self._attrs = None
    if pid != -1:
      self._realize()

  def exists(self):
    return self._exists

  def get(self, key):
    probe_key = self.ALIASES[key] if key in self.ALIASES else key
    return self._attrs.get(probe_key)

  def refresh(self, line=None):
    return self._realize() if line is None else self._realize_from_line(line)
