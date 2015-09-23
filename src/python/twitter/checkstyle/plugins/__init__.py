# ==================================================================================================
# Copyright 2014 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

__all__ = ('list_plugins',)

import inspect
import pkgutil
import sys

from ..common import CheckstylePlugin


def list_plugins():
  """Register all 'Command's from all modules in the current directory."""
  checkers = []
  for _, mod, ispkg in pkgutil.iter_modules(__path__):
    if ispkg:
      continue
    fq_module = '.'.join([__name__, mod])
    __import__(fq_module)
    for (_, kls) in inspect.getmembers(sys.modules[fq_module], inspect.isclass):
      if kls is not CheckstylePlugin and issubclass(kls, CheckstylePlugin):
        checkers.append(kls)
  return checkers
