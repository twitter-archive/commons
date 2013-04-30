from abc import abstractmethod
from copy import copy
from collections import defaultdict

from twitter.common.lang import Interface

class ProcessProvider(Interface):
  """
    A provider of process handles.  Basically an interface in front of procfs or ps.
  """

  class UnknownPidError(Exception): pass

  def __init__(self):
    self.clear()

  def clear(self, pids=None):
    """
      Clear pids from cached internal state.  If pids is None, clear everything.
    """
    if pids is None:
      self._raw = {}
      self._pids = set()
      self._pid_to_parent = {}
      self._pid_to_children = defaultdict(set)
      self._handles = {}
    else:
      for pid, ppid in self._pid_to_parent.items():
        try:
          self._pid_to_children[ppid].remove(pid)
        except KeyError:
          pass
      for pid in pids:
        self._raw.pop(pid, None)
        self._handles.pop(pid, None)
      self._pids = self._pids - set(pids)

  def _process_lines(self, lines):
    for line in lines:
      pid, ppid = self._translate_line_to_pid_pair(line)
      if pid is None: continue
      self._pids.add(pid)
      self._raw[pid] = line
      self._pid_to_parent[pid] = ppid
      self._pid_to_children[ppid].add(pid)

  def _raise_unless_has_pid(self, pid):
    if pid not in self._pids:
      raise ProcessProvider.UnknownPidError("Do not know about pid %s, call refresh()?" % pid)

  def pids(self):
    """Returns list of pids from the last collection."""
    return copy(self._pids)

  def _calculate_children(self, pid, current_set):
    new_children = copy(self._pid_to_children[pid])
    added = set()
    for new_child in new_children:
      if new_child not in current_set:
        added.add(new_child)
        current_set.add(new_child)
    for new_child in added:
      self._calculate_children(new_child, current_set)

  def children_of(self, pid, all=False):
    """
      By default returns set of pids of direct descendents of pid based upon
      last collection.  If all=True is set, then get all descendents of pid
      recursively.
    """
    self._raise_unless_has_pid(pid)
    if all:
      all_children = set()
      self._calculate_children(pid, all_children)
      return all_children
    else:
      return copy(self._pid_to_children[pid])

  def get_handle(self, pid):
    """Given a pid, return a ProcessHandle based upon the last collection."""
    self._raise_unless_has_pid(pid)
    return self._translate_line_to_handle(self._raw[pid])

  def collect_all(self):
    """Collect data from all processes."""
    self.clear()
    self._process_lines(self._collect_all())

  def collect_set(self, pidset):
    """Collect data from a subset of processes."""
    self.clear(pidset)
    self._process_lines(self._collect_set(pidset))

  # Required by provider implementations.
  @abstractmethod
  def _collect_all(self):
    """Collect and return all process information into unstructured line-based data."""

  @abstractmethod
  def _collect_set(self, pidset):
    """Collect and return a subset of process information into unstructured line-based data."""

  @abstractmethod
  def _translate_line_to_pid_pair(self, line):
    """Given a line, extract a pid and ppid from it."""

  @abstractmethod
  def _translate_line_to_handle(self, line):
    """Given a line, extract a ProcessHandle from it."""

  @staticmethod
  def _platform_compatible():
    """Returns true if this provider is compatible on the current platform."""
    raise NotImplementedError
