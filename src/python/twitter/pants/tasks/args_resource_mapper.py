# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

from __future__ import print_function

import os

from twitter.pants.java.jar import open_jar
from twitter.pants.targets import JavaLibrary, ScalaLibrary

from . import Task


RESOURCE_RELDIR = 'com/twitter/common/args/apt'
RESOURCE_BASENAME = 'cmdline.arg.info.txt'


class ArgsResourceMapper(Task):
  """Maps resource files generated by com.twitter.common#args-apt into a binary jar."""

  @classmethod
  def setup_parser(cls, option_group, args, mkflag):
    option_group.add_option(mkflag("include_all"), mkflag("include_all", negate=True),
                            dest="args_resource_mapper_include_all", default=False,
                            action="callback", callback=mkflag.set_bool,
                            help="[%default] Include all arg fields resources.")

  def __init__(self, context, select_targets, transitive, main):
    """
      :select_targets A predicate that selects the targets to create a trimmed cmdline args resource
                      file for.
      :transitive     If True, splits cmdline args resource info for all classes in the transitive
                      closure of classes depended on by the selected targets; otherwise, just
                      selects cmdline info for the classes owned by the selected targets directly.
      :main           True if the split cmdline arg resource info is for a main; False otherwise.
    """
    Task.__init__(self, context)

    self.select_targets = select_targets
    self.transitive = transitive

    # The args apt plugin uses a sequential suffix scheme to detect a family of cmdline args
    # resource files available on a classpath.  The 0th slot is normally skipped and reserved to
    # the cmdline arg resource file of a main.
    self.resource_index = 0 if main else 1

    context.products.require('jars', self.select_targets)
    context.products.require_data('classes_by_target')
    default_args_resource_mapper = [
      os.path.join(self.get_workdir(key='java_workdir', workdir='javac'), 'classes')
    ]
    self.classdirs = context.config.getlist('args-resource-mapper', 'classdirs',
                                            default=default_args_resource_mapper)
    self.include_all = context.options.args_resource_mapper_include_all

  def execute(self, targets):
    if self.classdirs:
      jarmap = self.context.products.get('jars')
      for target in filter(self.select_targets, targets):
        mapping = jarmap.get(target)
        if mapping:
          for basedir, jars in mapping.items():
            for jar in jars:
              self._addargsresources(os.path.join(basedir, jar), target)
        else:
          self.context.log.warn('No classes found for target %s' % target)

  def _addargsresources(self, jar, target):
    lines = set()
    for resourcedir in [os.path.join(classdir, RESOURCE_RELDIR) for classdir in self.classdirs]:
      if os.path.exists(resourcedir):
        for file in os.listdir(resourcedir):
          if file.startswith(RESOURCE_BASENAME):
            with open(os.path.join(resourcedir, file)) as resource:
              lines.update(resource.readlines())

    if lines:
      class Args(object):
        def __init__(self, context, transitive, classes_by_target):
          self.context = context
          self.classnames = set()

          def add_classnames(target):
            if isinstance(target, JavaLibrary) or isinstance(target, ScalaLibrary):
              target_products = classes_by_target.get(target)
              if target_products:
                for _, classes in target_products.rel_paths():
                  for cls in classes:
                    self.classnames.add(cls.replace('.class', '').replace('/', '.'))
              else:
                self.context.log.debug('No mapping for %s' % target)

          if transitive:
            target.walk(add_classnames, lambda t: t.is_internal)
          else:
            add_classnames(target)

        def matches(self, line):
          line = line.strip()
          if not line:
            return False
          components = line.split(' ')
          keyname = components[0]
          if keyname in ('positional', 'field'):
            # Line format: [key] class field
            return components[1] in self.classnames
          elif keyname == 'parser':
            # Line format: [key] parsed-class parser-class
            return components[2] in self.classnames
          elif keyname == 'verifier':
            # Line format: [key] verified-class verification-annotation-class verifier-class
            return components[2] in self.classnames and components[3] in self.classnames
          else:
            # Unknown line (comments, ws, unknown configuration types
            return True

      self._addargs(lines if self.include_all
                          else filter(Args(self.context,
                                           self.transitive,
                                           self.context.products.get_data('classes_by_target')).matches,
                                      lines),
                    jar,
                    target)

  def _addargs(self, lines, jarfile, target):
    def is_configurationinfo(line):
      line = line.strip()
      return line and not line.startswith('#')

    if any(filter(is_configurationinfo, lines)):
      resource = os.path.join(RESOURCE_RELDIR, '%s.%d' % (RESOURCE_BASENAME, self.resource_index))

      content = '# Created by pants goal args-apt\n'
      content += ''.join(sorted(lines))

      with open_jar(jarfile, 'a') as jar:
        jar.writestr(resource, content)
        self.context.log.debug('Added args-apt resource file %s for %s:'
                               '\n%s' % (resource, target, content))
