__author__ = 'Brian Wickman'

from process_provider_ps import ProcessProvider_PS
from process_provider_procfs import ProcessProvider_Procfs

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
