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

from . import Command

from common.collections import OrderedSet
from pants import (
  Address,
  CycleException,
  InternalTarget,
  Target,
  is_jvm,
  is_python,
)
from pants.ant import AntBuilder
from pants.ant.lib import (
  TRANSITIVITY_NONE,
  TRANSITIVITY_SOURCES,
  TRANSITIVITY_TESTS,
  TRANSITIVITY_ALL,
)
from pants.python import PythonBuilder

import traceback

_TRANSITIVITY_CHOICES = [
  TRANSITIVITY_NONE,
  TRANSITIVITY_SOURCES,
  TRANSITIVITY_TESTS,
  TRANSITIVITY_ALL
]
_VALID_TRANSITIVITIES = set(_TRANSITIVITY_CHOICES)

class Build(Command):
  """Builds a specified target."""

  def setup_parser(self, parser):
    parser.set_usage("\n"
                     "  %prog build (options) [spec] (build args)\n"
                     "  %prog build (options) [spec]... -- (build args)")
    parser.disable_interspersed_args()
    parser.add_option("--fast", action="store_true", dest = "is_meta", default = False,
                      help = "Specifies the build should be flattened before executing, this can "
                             "help speed up many builds.  Equivalent to the ! suffix BUILD target "
                             "modifier")
    parser.add_option("--ide", action="store_true", dest = "is_ide", default = False,
                      help = "Specifies the build should just do enough to get an IDE usable.")
    parser.add_option("-t", "--ide-transitivity", dest = "ide_transitivity", type = "choice",
                      choices = _TRANSITIVITY_CHOICES, default = TRANSITIVITY_TESTS,
                      help = "[%%default] Specifies IDE dependencies should be transitive for one "
                             "of: %s" % _TRANSITIVITY_CHOICES)
    parser.add_option("-q", "--quiet", action="store_true", dest = "quiet", default = False,
                      help = "Don't output result of empty targets")

    parser.epilog = """Builds the specified target(s).  Currently any additional arguments are
    passed straight through to the ant build system."""

  def __init__(self, root_dir, parser, argv):
    Command.__init__(self, root_dir, parser, argv)

    if not self.args:
      self.error("A spec argument is required")

    try:
      specs_end = self.args.index('--')
      if len(self.args) > specs_end:
        self.build_args = self.args.__getslice__(specs_end + 1, len(self.args) + 1)
      else:
        self.build_args = []
    except ValueError:
      specs_end = 1
      self.build_args = self.args[1:] if len(self.args) > 1 else []

    self.targets = OrderedSet()
    for spec in self.args.__getslice__(0, specs_end):
      try:
        address = Address.parse(root_dir, spec)
      except:
        self.error("Problem parsing spec %s: %s" % (spec, traceback.format_exc()))

      try:
        target = Target.get(address)
      except:
        self.error("Problem parsing BUILD target %s: %s" % (address, traceback.format_exc()))

      try:
        InternalTarget.check_cycles(target)
      except CycleException as e:
        self.error("Target contains an internal dependency cycle: %s" % e)

      if not target:
        self.error("Target %s does not exist" % address)
      if not target.address.is_meta:
        target.address.is_meta = self.options.is_meta or address.is_meta
      self.targets.add(target)

    self.is_ide = self.options.is_ide
    self.ide_transitivity = self.options.ide_transitivity

  def execute(self):
    print "Build operating on targets: %s" % self.targets

    jvm_targets = OrderedSet()
    python_targets = OrderedSet()
    for target in self.targets:
      if is_jvm(target):
        jvm_targets.add(target)
      elif is_python(target):
        python_targets.add(target)
      else:
        self.error("Cannot build target %s" % target)

    if jvm_targets:
      status = self._jvm_build(jvm_targets)
      if status != 0:
        return status

    if python_targets:
      status = self._python_build(python_targets)

    return status

  def _jvm_build(self, targets):
    try:
      # TODO(John Sirois): think about moving away from the ant backend
      executor = AntBuilder(self.error, self.root_dir, self.is_ide, self.ide_transitivity)
      if self.options.quiet:
        self.build_args.insert(0, "-logger")
        self.build_args.insert(1, "org.apache.tools.ant.NoBannerLogger")
        self.build_args.insert(2, "-q")
      return executor.build(targets, self.build_args)
    except:
      self.error("Problem executing AntBuilder for targets %s: %s" % (targets,
                                                                      traceback.format_exc()))

  def _python_build(self, targets):
    try:
      executor = PythonBuilder(self.error, self.root_dir)
      return executor.build(targets, self.build_args)
    except:
      self.error("Problem executing PythonBuilder for targets %s: %s" % (targets,
                                                                         traceback.format_exc()))
