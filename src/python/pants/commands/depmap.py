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

__author__ = 'John Sirois'

from . import Command

from pants import Address, Target

import traceback

class Depmap(Command):
  """Generates either a textual dependency tree or a graphviz digraph dotfile for the dependency set
  of a target."""

  def setup_parser(self, parser):
    parser.set_usage("%prog depmap (options) [spec]")
    parser.add_option("-i", "--internal-only", action="store_true", dest = "is_internal_only",
                      default = False, help = """Specifies that only internal dependencies should
                      be included in the graph output (no external jars).""")
    parser.add_option("-e", "--external-only", action="store_true", dest = "is_external_only",
                      default = False, help = """Specifies that only external dependencies should
                      be included in the graph output (only external jars).""")
    parser.add_option("-m", "--minimal", action="store_true", dest = "is_minimal",
                      default = False, help = """For a textual dependency tree, only prints a
                      dependency the 1st time it is encountered.  For graph output this does
                      nothing.""")
    parser.add_option("-s", "--separator", dest = "separator",
                      default = "-", help = """Specifies the separator to use between the
                      org/name/rev components of a dependency's fully qualified name.""")
    parser.add_option("-g", "--graph", action="store_true", dest = "is_graph", default = False,
                      help = """Specifies the internal dependency graph should be output in the dot
                      digraph format""")
    parser.epilog = """Generates either a textual dependency tree or a graphviz digraph dotfile for
    the dependency set of a java BUILD target."""

  def __init__(self, root_dir, parser, argv):
    Command.__init__(self, root_dir, parser, argv)

    if len(self.args) is not 1:
      self.error("Exactly one BUILD address is required.")

    if self.options.is_internal_only and self.options.is_external_only:
      self.error("At most one of external only or internal only can be selected.")

    spec = self.args[0]
    try:
      self.address = Address.parse(root_dir, spec)
    except:
      self.error("Problem parsing spec %s: %s" % (spec, traceback.format_exc()))

    self.is_internal_only = self.options.is_internal_only
    self.is_external_only = self.options.is_external_only
    self.is_minimal = self.options.is_minimal
    self.is_graph = self.options.is_graph

  def execute(self):
    target = Target.get(self.address)

    if self.is_graph:
      self._print_digraph(target)
    else:
      self._print_dependency_tree(target)

  def _dep_id(self, dependency):
    """Returns a tuple of dependency_id , is_internal_dep."""

    dep = dependency._create_template_data()
    params = dict(
      org = dep.org,
      name = dep.module,
      rev = dep.version,
      sep = self.options.separator,
    )

    if dep.version:
      return "%(org)s%(sep)s%(name)s%(sep)s%(rev)s" % params, False
    else:
      return "%(org)s%(sep)s%(name)s" % params, True

  def _print_dependency_tree(self, target):
    def print_dep(dep, indent):
      print("%s%s" % (indent * "  ", dep))

    def print_deps(printed, dep, indent = 0):
      dep_id, _ = self._dep_id(dep)
      if dep_id in printed:
        if not self.is_minimal:
          print_dep("*%s" % dep_id, indent)
      else:
        if not self.is_external_only:
          print_dep(dep_id, indent)
          printed.add(dep_id)
          indent += 1

        for internal_dep in dep.internal_dependencies:
          print_deps(printed, internal_dep, indent)

        if not self.is_internal_only:
          for jar_dep in dep.jar_dependencies:
            jar_dep_id, internal = self._dep_id(jar_dep)
            if not internal:
              if jar_dep_id not in printed or (not self.is_minimal and not self.is_external_only):
                print_dep(jar_dep_id, indent)
                printed.add(jar_dep_id)

    print_deps(set(), target)

  def _print_digraph(self, target):
    target_id, _ = self._dep_id(target)

    def print_dep(dep):
      dep_id, internal = self._dep_id(dep)
      science_styled = internal and not self.is_internal_only
      twitter_styled = not internal and dep.org.startswith('com.twitter')

      if science_styled:
        fmt = '  "%(id)s" [label="%(id)s", style="fill", fillcolor="#0084b4", fontcolor="white"];'
        print(fmt % { 'id': dep_id })
      elif twitter_styled:
        print('  "%s" [style="fill", fillcolor="#codeed"];' % dep_id)
      else:
        print('  "%s";' % dep_id)

    def print_deps(printed, dep):
      if dep not in printed:
        printed.add(dep)

        dep_id, _ = self._dep_id(dep)
        for dependency in dep.internal_dependencies:
          print_deps(printed, dependency)

        for jar in dep.jar_dependencies:
          jar_id, internal = self._dep_id(jar)
          output_candidate = (self.is_internal_only and internal) or (
            self.is_external_only and not internal) or (
            not self.is_internal_only and not self.is_external_only)

          if output_candidate and jar not in printed:
            print_dep(jar)
            printed.add(jar)

          if output_candidate:
            left_id = target_id if self.is_external_only else dep_id
            if (left_id, jar_id) not in printed:
              styled = internal and not self.is_internal_only
              print '  "%s" -> "%s"%s;' % (left_id, jar_id, ' [style="dashed"]' if styled else '')
              printed.add((left_id, jar_id))

    print('digraph "%s" {' % target._id)
    print_dep(target)
    print_deps(set(), target)
    print('}')
