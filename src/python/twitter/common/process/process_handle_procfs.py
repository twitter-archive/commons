import errno
import os
import pwd
import time

from .process_handle import ProcessHandleParserBase

class ProcessHandlersProcfs(object):
  BOOT_TIME = None
  @staticmethod
  def boot_time(now=None):
    now = now or time.time()
    if ProcessHandlersProcfs.BOOT_TIME is None:
      try:
        with open("/proc/uptime") as fp:
          uptime, _ = fp.read().split()
        ProcessHandlersProcfs.BOOT_TIME = now - float(uptime)
      except:
        ProcessHandlersProcfs.BOOT_TIME = 0
        pass
    return ProcessHandlersProcfs.BOOT_TIME

  @staticmethod
  def handle_time(_, value):
    return 1.0 * value / os.sysconf('SC_CLK_TCK')

  @staticmethod
  def handle_mem(_, value):
    return value * os.sysconf('SC_PAGESIZE')

  @staticmethod
  def handle_start_time(key, value):
    elapsed_after_system_boot = ProcessHandlersProcfs.handle_time(key, value)
    return time.time() - (ProcessHandlersProcfs.boot_time() + elapsed_after_system_boot)


class ProcessHandleProcfs(ProcessHandleParserBase):
  ATTRS = (
    """pid comm state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime
       stime cutime cstime priority nice num_threads itrealvalue starttime vsize rss rsslim
       startcode endcode startstack kstkesp kstkeip signal blocked sigignore sigcatch wchan nswap
       cnswap exit_signal processor rt_priority policy""".split())

  TYPE_MAP = {
            "pid":   "%d",         "comm":   "%s",       "state":  "%c",        "ppid":  "%d",
           "pgrp":   "%d",      "session":   "%d",      "tty_nr":  "%d",       "tpgid":  "%d",
          "flags":   "%u",       "minflt":  "%lu",     "cminflt": "%lu",      "majflt": "%lu",
        "cmajflt":  "%lu",        "utime":  "%lu",       "stime": "%lu",      "cutime": "%ld",
         "cstime":  "%ld",     "priority":  "%ld",        "nice": "%ld", "num_threads": "%ld",
    "itrealvalue":  "%ld",    "starttime": "%llu",       "vsize": "%lu",         "rss": "%ld",
         "rsslim":  "%lu",    "startcode":  "%lu",     "endcode": "%lu",  "startstack": "%lu",
        "kstkesp":  "%lu",      "kstkeip":  "%lu",      "signal": "%lu",     "blocked": "%lu",
      "sigignore":  "%lu",     "sigcatch":  "%lu",       "wchan": "%lu",       "nswap": "%lu",
         "cnswap":  "%lu",  "exit_signal":   "%d",   "processor":  "%d", "rt_priority":  "%u",
         "policy":   "%u"
  }

  ALIASES = {
    'vsz': 'vsize',
    'stat': 'state',
  }

  HANDLERS = {
    'utime': ProcessHandlersProcfs.handle_time,
    'stime': ProcessHandlersProcfs.handle_time,
    'cutime': ProcessHandlersProcfs.handle_time,
    'cstime': ProcessHandlersProcfs.handle_time,
    'starttime': ProcessHandlersProcfs.handle_start_time,
    'rss': ProcessHandlersProcfs.handle_mem
  }

  def _produce(self):
    try:
      with open("/proc/%s/stat" % self._pid) as fp:
        return fp.read()
    except IOError as e:
      if e.errno not in (errno.ENOENT, errno.ESRCH):
        raise e

  def cpu_time(self):
    return self.get('utime') + self.get('stime')

  def wall_time(self):
    return self.get('starttime')

  def pid(self):
    return self.get('pid')

  def ppid(self):
    return self.get('ppid')

  def user(self):
    try:
      uid = os.stat('/proc/%s' % self.pid()).st_uid
      try:
        pwd_entry = pwd.getpwuid(uid)
      except KeyError:
        return None
      return pwd_entry.pw_name
    except OSError:
      return None

  def cwd(self):
    try:
      return os.readlink('/proc/%s/cwd' % self.pid())
    except OSError:
      # Likely permission denied or no such file or directory
      return None

  def cmdline(self):
    try:
      with open('/proc/%s/cmdline' % self.pid(), 'r') as infile:
        return infile.read().replace('\0', ' ')
    except OSError:
      # Likely permission denied or no such file or directory
      return None

