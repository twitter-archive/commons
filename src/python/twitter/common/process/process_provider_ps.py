import os
from process_handle_ps import ProcessHandlePs
from process_provider import ProcessProvider

class ProcessProvider_PS(ProcessProvider):
  """
    Process provider on top of the "ps" utility.
  """
  def _collect_all(self):
    return os.popen('ps ax -o %s' % (','.join(ProcessHandlePs.ATTRS))).readlines()

  def _collect_set(self, pidset):
    return os.popen('ps -p %s -o %s' % (
      ','.join(map(str, pidset)), ','.join(ProcessHandlePs.ATTRS))).readlines()

  def _translate_line_to_pid_pair(self, line):
    sline = line.split()
    try:
      return (int(sline[ProcessHandlePs.ATTRS.index('pid')]),
              int(sline[ProcessHandlePs.ATTRS.index('ppid')]))
    except:
      return None, None

  def _translate_line_to_handle(self, line):
    return ProcessHandlePs.from_line(line)

  @staticmethod
  def _platform_compatible():
    # Compatible with any Unix flavor with SUSv2+ conformant 'ps'.
    return True
