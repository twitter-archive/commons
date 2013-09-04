# ==================================================================================================
# Copyright 2011 Twitter, Inc.
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

from collections import defaultdict

from twitter.common.collections import maybe_list
from twitter.common.lang import Compatibility, Singleton
from twitter.common.util import topological_sort, DependencyCycle


class AppModule(Singleton):
  """
    Base class for application module registration:
      - module setup
      - module teardown
      - module dependencies

    If your application needs setup/teardown functionality, override the
    setup_function and teardown_function respectively.
  """

  class Error(Exception): pass
  class Unimplemented(Error): pass
  class DependencyCycle(Error): pass

  _MODULE_REGISTRY = {}
  _MODULE_DEPENDENCIES = defaultdict(set)

  @classmethod
  def module_registry(cls):
    return cls._MODULE_REGISTRY

  @classmethod
  def module_dependencies(cls):
    return cls._MODULE_DEPENDENCIES

  # for testing
  @classmethod
  def clear_registry(cls):
    cls._MODULE_REGISTRY = {}
    cls._MODULE_DEPENDENCIES = defaultdict(set)

  def __init__(self, label, dependencies=None, dependents=None, description=None):
    """
      @label = the label that identifies this module for dependency management
      @dependencies = a string or list of strings of modules this module depends upon (optional)
      @dependents = a string or list of strings of modules that depend upon this module (optional)
      @description = a one-liner describing this application module, e.g. "Logging module"
    """
    self._label = label
    self._description = description
    self._dependencies = maybe_list(dependencies or [])
    self._dependents = maybe_list(dependents or [])
    self._MODULE_REGISTRY[label] = self
    self._MODULE_DEPENDENCIES[label].update(self._dependencies)
    for dependent in self._dependents:
      self._MODULE_DEPENDENCIES[dependent].add(label)
    try:
      list(topological_sort(self._MODULE_DEPENDENCIES))
    except DependencyCycle:
      raise AppModule.DependencyCycle("Found a cycle in app module dependencies!")

  def description(self):
    return self._description

  def label(self):
    return self._label

  def dependencies(self):
    return self._dependencies if self._dependencies else []

  def setup_function(self):
    """
      The setup function for this application module.  If you wish to have a
      setup function, override this method.  This is called by app.main()
      just before running the main method of the application.

      To control the order in which this setup function is run with respect
      to other application modules, you may list a set of module
      dependencies in the AppModule constructor.
    """
    raise AppModule.Unimplemented()

  def teardown_function(self):
    """
      The teardown function for this application module.  This is called if
      the application exits "cleanly":
         if your main() method returns
         if your application calls sys.exit()
         your application gets a SIGINT

      The teardown functions are run in an order compatible with the reverse
      topological order of the setup functions.
    """
    raise AppModule.Unimplemented()
