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

from . import Command

from pants import Address, Target
from pants.ant import AntBuilder
from pants.ant.lib import (
  TRANSITIVITY_NONE,
  TRANSITIVITY_SOURCES,
  TRANSITIVITY_TESTS,
  TRANSITIVITY_ALL,
)
from pants.python import PythonBuilder

import traceback

_VALID_TRANSITIVITIES = set([
  TRANSITIVITY_NONE,
  TRANSITIVITY_SOURCES,
  TRANSITIVITY_TESTS,
  TRANSITIVITY_ALL
])

_DEFAULT_TRANSITIVITY = TRANSITIVITY_TESTS

class Build(Command):
  """Builds a specified target."""

  def setup_parser(self, parser):
    parser.set_usage("%prog build (options) [spec] (build args)")
    parser.disable_interspersed_args()
    parser.add_option("--fast", action="store_true", dest = "is_meta", default = False,
                      help = "Specifies the build should be flattened before executing, this can "
                             "help speed up many builds.  Equivalent to the ! suffix BUILD target "
                             "modifier")
    parser.add_option("--ide", action="store_true", dest = "is_ide", default = False,
                      help = "Specifies the build should just do enough to get an IDE usable.")
    parser.add_option("--ide-transitivity", dest = "ide_transitivity", default = None,
                      help = "Specifies IDE dependencies should be transitive for: sources, tests, "
                             "all or none")
    parser.add_option("-q", "--quiet", action="store_true", dest = "quiet", default = False,
                      help = "Don't output result of empty targets")

    parser.epilog = """Builds the specified target.  Currently any additional arguments are passed
    straight through to the ant build system."""

  def __init__(self, root_dir, parser, argv):
    Command.__init__(self, root_dir, parser, argv)

    if not self.args:
      self.error("A spec argument is required")

    spec = self.args[0]
    try:
      self.address = Address.parse(root_dir, spec)
    except:
      self.error("Problem parsing spec %s: %s" % (spec, traceback.format_exc()))

    if not self.address.is_meta:
      self.address.is_meta = self.options.is_meta

    self.is_ide = self.options.is_ide
    if self.options.ide_transitivity:
      if not self.is_ide:
        self.error("--ide-transitivity only applies when using --ide")
      elif self.options.ide_transitivity not in _VALID_TRANSITIVITIES:
        self.error("%s is not a valid value for --ide-transitivity" % self.options.ide_transitivity)
      self.ide_transitivity = self.options.ide_transitivity
    else:
      self.ide_transitivity = _DEFAULT_TRANSITIVITY

    self.build_args = self.args[1:] if len(self.args) > 1 else []

  def execute(self):
    print "Build operating on address: %s" % self.address

    try:
      target = Target.get(self.address)
    except:
      self.error("Problem parsing BUILD target %s: %s" % (self.address, traceback.format_exc()))

    if not target:
      self.error("Target %s does not exist" % self.address)

    return self._build(target)

  def _build(self, target):
    if self.address.buildfile.relpath.startswith('tests/python'):
      return self._python_build(target)
    else:
      return self._jvm_build(target)


  def _jvm_build(self, target):
    try:
      # TODO(John Sirois): think about moving away from the ant backend
      executor = AntBuilder(self.error, self.root_dir, self.is_ide, self.ide_transitivity)
      if self.options.quiet:
        self.build_args.insert(0, "-logger")
        self.build_args.insert(1, "org.apache.tools.ant.NoBannerLogger")
        self.build_args.insert(2, "-q")
      return executor.build(target, self.address.is_meta, self.build_args)
    except:
      self.error("Problem executing AntBuilder for target %s: %s" % (self.address,
                                                                     traceback.format_exc()))

  def _python_build(self, target):
    try:
      executor = PythonBuilder(self.error, self.root_dir)
      return executor.build(target, self.address.is_meta, self.build_args)
    except:
      self.error("Problem executing PythonBuilder for target %s: %s" % (self.address,
                                                                        traceback.format_exc()))
