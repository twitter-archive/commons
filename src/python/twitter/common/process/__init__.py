import atexit
import os
import sys

from twitter.common.dirutil import lock_file

from .process_provider_ps import ProcessProvider_PS
from .process_provider_procfs import ProcessProvider_Procfs


_PIDFILE = None

def spawn_daemon(pidfile=None, stdout='/dev/null', stderr='/dev/null', quiet=False):
  """
    Spawns a new daemon process without terminating the current process.
    Returns ``True`` to daemon process and ``False`` to curent process.
    `pidfile`: 
      :Deafult: ``None``
      The pid file to store the daemon procees pid.
    `stdout`:
      :Default: ``'/dev/null'``
      Location to redirect stdout
    `stderr`:
      :Default: ``'/dev/null'``
      Location to redirect stderr
    `quiet`:
      :Default: ``False``
      If ``True`` supresses output to stdout and stderr

    Typical Usage:
    Import
    >>> from twitter.common.process import spawn_daemon

    >>> def do_daemon_process():
    ...   while(1):
    ...     time.sleep(1)
    ...     print("I am asleep for ever")

    Call spawn_daemon
    >>> if spawn_daemon("/tmp/pid", quiet=True):
    ...   do_daemon_process()

    Returns to python interactive shell and continues the execution
    >>> print("I am continuing with main")
    I am continuing with main
   >>>

   The daemon process is running
   [bash]$ ps -p `cat /tmp/pid`
     PID TTY           TIME CMD
   33095 ??         0:21.50 /usr/bin/python2.6 /var/folders/t_/ck6c/T/tmpolW2xb
  """
  return not _daemonize(pidfile, stdout, stderr, quiet, exit_parent=False)


def daemonize(pidfile=None, stdout='/dev/null', stderr='/dev/null', quiet=False):
  """
    Exits the current process and starts a new daemon process.
    `pidfile`: 
      :Deafult: ``None``
      The pid file to store the daemon procees pid.
    `stdout`:
      :Default: ``'/dev/null'``
      Location to redirect stdout
    `stderr`:
      :Default: ``'/dev/null'``
      Location to redirect stderr
    `quiet`:
      :Default: ``False``
      If ``True`` supresses output to stdout and stderr

    Typical Usage:
    Import
    >>> from twitter.common.process import daemonize

    Define the daemon method and call daemonize
    >>> def run_daemon():
    ...   daemonize('/tmp/pid')
    ...   while(1):
    ...    print("i am asleep...")

    Run 
    >>> run_daemon()
        Writing pid 32758 into /tmp/pid
    [bash]$
    The current python interactive shell terminates and a daemon_process 32758 is running
    [bash]$ ps -p 32758
      PID TTY           TIME CMD
    32758 ??         0:39.68 /usr/bin/python2.6 /var/folders/t_/ck6c3z/T/tmptkO6Wc
    """
  _daemonize(pidfile, stdout, stderr, quiet, exit_parent=True)


# TODO(wickman)  Leverage PEP-3143 http://pypi.python.org/pypi/python-daemon/
def _daemonize(pidfile, stdout, stderr, quiet, exit_parent):
  global _PIDFILE
  def daemon_fork(exit_parent=True):
    try:
      if os.fork() > 0:
        if exit_parent:
          os._exit(0)
        return True
      return False
    except OSError as e:
      sys.stderr.write('Failed to fork: %s\n' % e)
      sys.exit(1)

  parent = daemon_fork(exit_parent)
  if parent:
    return True
  os.setsid()
  daemon_fork()

  if pidfile:
    _PIDFILE = lock_file(pidfile, 'w+')
    if _PIDFILE:
      pid = os.getpid()
      if not quiet:
        sys.stderr.write('Writing pid %s into %s\n' % (pid, pidfile))
      _PIDFILE.write(str(pid))
      _PIDFILE.flush()
    else:
      if not quiet:
        sys.stderr.write('Could not acquire pidfile %s, another process running!\n' % pidfile)
      sys.exit(1)

    def shutdown():
      os.unlink(pidfile)
      _PIDFILE.close()
    atexit.register(shutdown)

  sys.stdin = open('/dev/null', 'r')
  sys.stdout = open(stdout, 'a+')
  sys.stderr = open(stderr, 'a+', 1)


class ProcessProviderFactory(object):
  """
    A factory for producing platform-appropriate ProcessProviders.

    Typical use-cases:

      Import
      >>> from twitter.common.process import ProcessProviderFactory
      >>> ps = ProcessProviderFactory.get()

      Run a collection of all pids
      >>> ps.collect_all()

      Get a ProcessHandle to the init process
      >>> init = ps.get_handle(1)
      >>> init
      <twitter.common.process.process_handle_ps.ProcessHandlePs object at 0x1004ad950>

      Get stats
      >>> init.cpu_time()
      7980.0600000000004
      >>> init.user()
      'root'
      >>> init.wall_time()
      6485509.0
      >>> init.pid()
      1
      >>> init.ppid()
      0

      Refresh stats
      >>> init.refresh()
      >>> init.cpu_time()
      7982.9700000000003

      Introspect the process tree
      >>> list(ps.children_of(init.pid()))
      [10, 11, 12, 13, 14, 15, 16, 17, 26, 32, 37, 38, 39, 40, 42, 43, 45,
       51, 59, 73, 108, 140, 153, 157, 162, 166, 552, 1712, 1968, 38897,
       58862, 63321, 64513, 66458, 68598, 78610, 85633, 91019, 97271]

      Aggregations
      >>> sum(map(lambda pid: ps.get_handle(pid).cpu_time(), ps.children_of(init.pid())))
      228574.40999999995

      Collect data from a subset of processes
      >>> ps.collect_set(ps.children_of(init.pid()))

      Re-evaluate
      >>> sum(map(lambda pid: ps.get_handle(pid).cpu_time(), ps.children_of(init.pid())))
      228642.19999999998
  """
  PROVIDERS = [
    ProcessProvider_Procfs,
    ProcessProvider_PS
  ]

  @staticmethod
  def get():
    """
      Return a platform-specific ProcessProvider.
    """
    for provider in ProcessProviderFactory.PROVIDERS:
      if provider._platform_compatible():
        return provider()
