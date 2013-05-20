import os
import subprocess
from process_handle import ProcessHandle, ProcessHandleParserBase

class ProcessHandlersPs(object):
  @staticmethod
  def handle_mem(_, value):
    return value * 1024

  @staticmethod
  def handle_elapsed(_, value):
    seconds = 0

    unpack = value.split('-')
    if len(unpack) == 2:
      seconds += int(unpack[0]) * 86400
      unpack = unpack[1]
    else:
      unpack = unpack[0]

    unpack = unpack.split(':')
    mult = 1.0
    for k in range(len(unpack), 0, -1):
      seconds += float(unpack[k-1]) * mult
      mult    *= 60

    return seconds

class ProcessHandlePs(ProcessHandle, ProcessHandleParserBase):
  ATTRS = [ 'user', 'pid', 'ppid', 'pcpu', 'rss', 'vsz', 'stat', 'etime', 'time', 'comm' ]

  TYPE_MAP = {
     'user': '%s',  'pid': '%d', 'ppid': '%d', 'pcpu': '%f', 'rss': '%d', 'vsz': '%d', 'stat': '%s',
    'etime': '%s', 'time': '%s', 'comm': '%s'
  }

  HANDLERS = {
    'rss':    ProcessHandlersPs.handle_mem,
    'vsz':    ProcessHandlersPs.handle_mem,
    'etime':  ProcessHandlersPs.handle_elapsed, # [[dd-]hh:]mm:ss.ds
    'time':   ProcessHandlersPs.handle_elapsed, # [[dd-]hh:]mm:ss.ds
  }

  ALIASES = {
    'starttime': 'etime',
  }

  def _get_process_attrs(self, attrs):
    try:
      data = os.popen('ps -p %s -o %s' % (self._pid, ','.join(attrs))).readlines()
      if len(data) > 1:
        return data[-1]
    except:
      return None

  def _produce(self):
    return self._get_process_attrs(ProcessHandlePs.ATTRS)

  def cpu_time(self):
    return self.get('time') or 0.0

  def wall_time(self):
    return self.get('starttime') or 0.0

  def pid(self):
    return self.get('pid')

  def ppid(self):
    return self.get('ppid')

  def user(self):
    return self.get('user')

  def cwd(self):
    try:
      lsof = subprocess.Popen(('lsof -a -p %s -d cwd -Fn' % self.pid()).split(),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
      stdout, _ = lsof.communicate()
      for line in stdout.split('\n'):
        if line.startswith('n'):
          return line[1:]
    except OSError:
      return None

  def cmdline(self):
    # 'comm' is just the base cmd, this returns the cmd with all the arguments.
    # We don't read 'command' on the initial ps call, because the result contains spaces, and
    # our scanf-like parsing code won't read it. This isn't a performance issue in current usage.
    return self._get_process_attrs(['command']).strip()

