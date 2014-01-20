import os

from .process_handle_procfs import ProcessHandleProcfs
from .process_provider import ProcessProvider

def filter_map(fn, lst):
  return filter(lambda return_value: return_value is not None, map(fn, lst))

class ProcessProvider_Procfs(ProcessProvider):
  """
    ProcessProvider on top of procfs.
  """
  def _collect_all(self):
    def try_int(value):
      try:
        return int(value)
      except ValueError:
        return None
    return self._collect_set(filter_map(try_int, os.listdir('/proc')))

  def _collect_set(self, pidset):
    def try_read_pid(pid):
      try:
        with open('/proc/%s/stat' % pid) as fp:
          return fp.read()
      except IOError:
        return None
    return filter_map(try_read_pid, pidset)

  def _translate_line_to_pid_pair(self, line):
    sline = line.split()
    try:
      return (int(sline[ProcessHandleProcfs.ATTRS.index('pid')]),
              int(sline[ProcessHandleProcfs.ATTRS.index('ppid')]))
    except:
      return None, None

  def _translate_line_to_handle(self, line):
    return ProcessHandleProcfs.from_line(line)

  @staticmethod
  def _platform_compatible():
    # Compatible with any Linux >=2.5.19, but could be easily adapted
    # to much earlier iterations (2.2.x+)
    return os.uname()[0] == 'Linux'
