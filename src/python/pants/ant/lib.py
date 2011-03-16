# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from pants import (
  Builder,
  Generator,
  JavaTarget,
  JavaTests,
  ScalaTests,
)

import bang
import ide
import os
import shutil
import subprocess
import traceback

TRANSITIVITY_NONE = 'none'
TRANSITIVITY_SOURCES = 'sources'
TRANSITIVITY_TESTS = 'tests'
TRANSITIVITY_ALL = 'all'

class AntBuilder(Builder):

  @classmethod
  def generate_ivy(cls, root_dir, output_filename, target):
    """Outputs an ivy.xml file to output_filename for the given java target"""

    AntBuilder._check_target(target)

    library_template_data = target._create_template_data()
    AntBuilder._generate(root_dir, 'ivy', library_template_data, output_filename)

  @classmethod
  def _generate(cls, root_dir, template, template_data, output_filename):
    with open(output_filename, 'w') as output:
      template_path = os.path.join(AntBuilder._template_basedir, '%s.mk' % template)
      generator = Generator(template_path, root_dir, template_data)
      generator.write(output)

  @classmethod
  def _check_target(cls, target):
    assert isinstance(target, JavaTarget), \
      "AntBuilder can only build JavaTargets, given %s" % str(target)

  def __init__(self, ferror, root_dir, is_ide, ide_transitivity):
    Builder.__init__(self, ferror, root_dir)
    self.is_ide = is_ide
    self.ide_transitivity = ide_transitivity

  def build(self, target, is_meta, args):
    java_target = self._resolve_target(target, is_meta)

    extrabuildflags = set()

    workspace_root = os.path.join(self.root_dir, '.pants.d')
    if os.path.exists(workspace_root):
      shutil.rmtree(workspace_root)
    os.makedirs(workspace_root)

    buildxml = self.create_ant_builds(workspace_root, dict(), extrabuildflags, java_target)

    buildflags = []
    if extrabuildflags:
      buildflags.extend(extrabuildflags)

    # TODO(John Sirois): introduce java_binary and only allow buildflags from those and disallow
    # java_binary as a pants dep - they must be leaf
    if java_target.buildflags:
      buildflags.extend(java_target.buildflags)

    antargs = [ 'ant', '-f', '"%s"' % buildxml ]

    if buildflags:
      antargs.extend(buildflags)

    if args:
      antargs.extend(args)

    print 'AntBuilder executing (ANT_OPTS="%s") %s' % (os.environ['ANT_OPTS'], ' '.join(antargs))
    return subprocess.call(antargs)

  def create_ant_builds(self, workspace_root, targets, flags, target):
    if target._id in targets:
      return targets[target._id]

    try:
      library_template_data = target._create_template_data()
    except:
      self.ferror("Problem creating template data for %s(%s): %s" %
        (type(target).__name__, target.parse_context, traceback.format_exc()))

    workspace = os.path.join(workspace_root, library_template_data.id)
    if not os.path.exists(workspace):
      os.makedirs(workspace)

    ivyxml = os.path.join(workspace, 'ivy.xml')
    AntBuilder._generate(self.root_dir, 'ivy', library_template_data, ivyxml)

    buildxml = os.path.join(workspace, 'build.xml')
    if target.custom_antxml_path:
      shutil.copyfile(target.custom_antxml_path, buildxml)
      pants_buildxml = os.path.join(workspace, 'pants-build.xml')
      flags.add('-Dpants.build.file=pants-build.xml')
    else:
      pants_buildxml = buildxml

    build_template = os.path.join(library_template_data.template_base, 'build')

    AntBuilder._generate(self.root_dir, build_template, library_template_data, pants_buildxml)

    targets[target._id] = buildxml

    for additional_library in target.internal_dependencies:
      self.create_ant_builds(workspace_root, targets, flags, additional_library)

    return buildxml

  def _resolve_target(self, target, is_meta):
    AntBuilder._check_target(target)

    if self.is_ide:
      def is_transitive():
        if self.ide_transitivity == TRANSITIVITY_TESTS:
          return lambda target: type(target) in set([JavaTests, ScalaTests])
        if self.ide_transitivity == TRANSITIVITY_ALL:
          return lambda target: True
        if self.ide_transitivity == TRANSITIVITY_NONE:
          return lambda target: False
        if self.ide_transitivity == TRANSITIVITY_SOURCES:
          return lambda target: type(target) not in set([JavaTests, ScalaTests])

      is_transitive = is_transitive()
      return target.do_in_context(lambda: ide.extract_target(target, is_transitive))
    elif is_meta:
      return target.do_in_context(lambda: bang.extract_target(target))
    else:
      return target

  _template_basedir = os.path.join(os.path.dirname(__file__), 'templates')
