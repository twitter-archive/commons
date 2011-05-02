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

from lib import (
  Address,
  BuildFile,
  CycleException,
  ExportableJvmLibrary,
  InternalTarget,
  JarDependency,
  JavaLibrary,
  JavaProtobufLibrary,
  JvmTarget,
  JavaTests,
  JavaThriftLibrary,
  Pants,
  ParseContext,
  PythonTarget,
  PythonTests,
  ScalaLibrary,
  ScalaTests,
  Target,
  TargetWithSources,
)

from generator import (
  Builder,
  Generator,
  TemplateData,
)

from util import (
  globs,
  has_sources,
  is_exported,
  is_internal,
  is_java,
  is_jvm,
  is_python,
  is_scala,
  is_test,
  rglobs,
)

from lib import (
  Artifact as artifact,
  Exclude as exclude,
  JarDependency as jar,
  JarLibrary as jar_library,
  JavaLibrary as java_library,
  JavaProtobufLibrary as java_protobuf_library,
  JavaThriftLibrary as java_thrift_library,
  JavaTests as java_tests,
  Pants as pants,
  PythonTests as python_tests,
  Repository as repo,
  ScalaLibrary as scala_library,
  ScalaTests as scala_tests,
)

__all__ = (
  'artifact',
  'exclude',
  'globs',
  'jar',
  'jar_library',
  'java_library',
  'java_protobuf_library',
  'java_tests',
  'java_thrift_library',
  'pants',
  'python_tests',
  'repo',
  'rglobs',
  'scala_library',
  'scala_tests',
)
