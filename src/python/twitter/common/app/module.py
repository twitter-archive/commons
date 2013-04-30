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

  class Unimplemented(Exception): pass
  class DependencyCycle(Exception): pass
  _MODULE_REGISTRY = {}
  _MODULE_DEPENDENCIES = {}

  @staticmethod
  def module_registry():
    return AppModule._MODULE_REGISTRY

  @staticmethod
  def module_dependencies():
    return AppModule._MODULE_DEPENDENCIES

  # for testing
  @staticmethod
  def clear_registry():
    AppModule._MODULE_REGISTRY = {}
    AppModule._MODULE_DEPENDENCIES = {}

  def __init__(self, label, dependencies=None, description=None):
    """
      @label = the label that identifies this module for dependency management
      @dependencies = a string or list of strings of modules this module depends upon (optional)
      @description = a one-liner describing this application module, e.g. "Logging module"
    """
    self._label = label
    self._description = description
    if isinstance(dependencies, list):
      self._dependencies = set(dependencies)
    elif isinstance(dependencies, Compatibility.string):
      self._dependencies = set([dependencies])
    elif dependencies is None:
      self._dependencies = set()
    else:
      raise TypeError('Dependencies should be None, string or list of strings, got: %s' %
        type(dependencies))
    AppModule._MODULE_REGISTRY[label] = self
    AppModule._MODULE_DEPENDENCIES[label] = self._dependencies
    try:
      list(topological_sort(AppModule._MODULE_DEPENDENCIES))
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
