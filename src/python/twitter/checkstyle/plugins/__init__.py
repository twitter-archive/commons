__all__ = ('list_plugins',)

import inspect
import pkgutil
import sys

from ..common import CheckstylePlugin


def list_plugins():
  """Register all 'Command's from all modules in the current directory."""
  checkers = []
  for _, mod, ispkg in pkgutil.iter_modules(__path__):
    if ispkg: continue
    fq_module = '.'.join([__name__, mod])
    __import__(fq_module)
    for (_, kls) in inspect.getmembers(sys.modules[fq_module], inspect.isclass):
      if kls is not CheckstylePlugin and issubclass(kls, CheckstylePlugin):
        checkers.append(kls)
  return checkers
