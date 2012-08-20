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

from __future__ import print_function

import os
import sys

_VERSION = '0.0.3'

def get_version():
  return _VERSION


_BUILD_ROOT = None

def get_buildroot():
  global _BUILD_ROOT
  if not _BUILD_ROOT:
    if 'PANTS_BUILD_ROOT' in os.environ:
      _BUILD_ROOT = os.path.realpath(os.environ['PANTS_BUILD_ROOT'])
    else:
      build_root = os.path.abspath(os.getcwd())
      while not os.path.exists(os.path.join(build_root, '.git')):
        if build_root != os.path.dirname(build_root):
          build_root = os.path.dirname(build_root)
        else:
          print('Could not find .git root!', file=sys.stderr)
          sys.exit(1)
      _BUILD_ROOT = os.path.realpath(build_root)
  return _BUILD_ROOT


import fnmatch
import glob

from functools import reduce

from twitter.pants.base import Fileset


def globs(*globspecs):
  """Returns a Fileset that combines the lists of files returned by glob.glob for each globspec."""

  def combine(files, globspec):
    return files ^ set(glob.glob(globspec))
  return Fileset(lambda: reduce(combine, globspecs, set()))


def rglobs(*globspecs):
  """Returns a Fileset that does a recursive scan under the current directory combining the lists of
  files returned that would be returned by glob.glob for each globspec."""

  root = os.curdir
  def recursive_globs():
    for base, _, files in os.walk(root):
      for filename in files:
        path = os.path.relpath(os.path.normpath(os.path.join(base, filename)), root)
        for globspec in globspecs:
          if fnmatch.fnmatch(path, globspec):
            yield path

  return Fileset(lambda: set(recursive_globs()))


from twitter.pants.targets import *

# aliases
annotation_processor = AnnotationProcessor
artifact = Artifact
bundle = Bundle
credentials = Credentials
dependencies = jar_library = JarLibrary
doc = Doc
egg = PythonEgg
exclude = Exclude
fancy_pants = Pants
jar = JarDependency
java_library = JavaLibrary
java_protobuf_library = JavaProtobufLibrary
java_tests = JavaTests
java_thrift_library = JavaThriftLibrary
# TODO(Anand) Remove this from pants proper when a code adjoinment mechanism exists
# or ok if/when thriftstore is open sourced as well
java_thriftstore_dml_library = JavaThriftstoreDMLLibrary
jvm_binary = JvmBinary
jvm_app = JvmApp
page = Page
python_binary = PythonBinary
python_library = PythonLibrary
python_antlr_library = PythonAntlrLibrary
python_requirement = PythonRequirement
python_thrift_library = PythonThriftLibrary
python_tests = PythonTests
python_test_suite = PythonTestSuite
repo = Repository
scala_library = ScalaLibrary
scala_tests = ScalaTests
scalac_plugin = ScalacPlugin
source_root = SourceRoot
wiki = Wiki


def has_sources(target):
  """Returns True if the target has sources."""
  return target.has_label('sources')


def is_exported(target):
  """Returns True if the target provides an artifact exportable from the repo."""
  return target.has_label('exportable')


def is_internal(target):
  """Returns True if the target is internal to the repo (ie: it might have dependencies)."""
  return target.has_label('internal')


def is_jvm(target):
  """Returns True if the target produces jvm bytecode."""
  return target.has_label('jvm')


def has_jvm_targets(targets):
  """Returns true if the given sequence of targets contains at least one jvm target as determined
  by is_jvm(...)"""

  return len(list(extract_jvm_targets(targets))) > 0


def extract_jvm_targets(targets):
  """Returns an iterator over the jvm targets the given sequence of targets resolve to.  The given
  targets can be a mix of types and any non jvm targets (as determined by is_jvm(...) will be
  filtered out from the returned iterator."""

  for target in targets:
    if target is None:
      print('Warning! Null target!', file=sys.stderr)
      continue
    for real_target in target.resolve():
      if is_jvm(real_target):
        yield real_target


def is_codegen(target):
  """Returns True if the target is a codegen target."""
  return target.has_label('codegen')

def is_doc(target):
  """Returns True if the target is a documentation target."""
  return target.has_label('doc')


def is_jar_library(target):
  """Returns True if the target is an external jar library."""
  return target.has_label('jars')


def is_java(target):
  """Returns True if the target has or generates java sources."""
  return target.has_label('java')


def is_apt(target):
  """Returns True if the target exports an annotation processor."""
  return target.has_label('apt')


def is_python(target):
  """Returns True if the target has python sources."""
  return target.has_label('python')


def is_scala(target):
  """Returns True if the target has scala sources."""
  return target.has_label('scala')


def is_scalac_plugin(target):
  """Returns True if the target builds a scalac plugin."""
  return target.has_label('scalac_plugin')


def is_test(t):
  """Returns True if the target is comprised of tests."""
  return t.has_label('tests')


def is_jar_dependency(dep):
  """Returns True if the dependency is an external jar."""
  return isinstance(dep, JarDependency)


# bind this as late as possible
pants = fancy_pants

# bind tasks and goals below utility functions they use from above
from twitter.pants.base import Config
from twitter.pants.goal import Context, Goal, Group, Phase
from twitter.pants.tasks import Task, TaskError

goal = Goal
group = Group
phase = Phase

__all__ = (
  'annotation_processor',
  'artifact',
  'bundle',
  'credentials',
  'dependencies',
  'doc',
  'exclude',
  'egg',
  'get_buildroot',
  'get_version',
  'globs',
  'goal',
  'group',
  'is_apt',
  'is_doc',
  'is_exported',
  'is_internal',
  'is_jar_library',
  'is_java',
  'is_jvm',
  'is_python',
  'is_scala',
  'is_test',
  'jar',
  'jar_library',
  'java_library',
  'java_protobuf_library',
  'java_tests',
  'java_thrift_library',
  'java_thriftstore_dml_library',
  'jvm_app',
  'jvm_binary',
  'page',
  'pants',
  'phase',
  'python_antlr_library',
  'python_binary',
  'python_library',
  'python_requirement',
  'python_tests',
  'python_test_suite',
  'python_thrift_library',
  'repo',
  'rglobs',
  'scala_library',
  'scala_tests',
  'scalac_plugin',
  'source_root',
  'wiki',
  'Config',
  'Context',
  'JavaLibrary',
  'JavaTests',
  'Task',
  'TaskError',
)
