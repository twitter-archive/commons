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

"""
BUILD files can use the full python language.  They define modules that can have targets executed
against. Modules are created by calling functions exposed by the pants library that define modules.
For example, to define a module that is a java library, we can invoke pants.java_library(...).

To invoke a set set of targets against a module you tell pants which module by pointing to a BUILD
file with the -f switch.  For example, any of the following would attempt to compile this BUILD
file's base module:
./pants -f src/java/com/twitter/common/base
./pants -f src/java/com/twitter/common/base:base
./pants -f src/java/com/twitter/common/base/
./pants -f src/java/com/twitter/common/base/:base
./pants -f src/java/com/twitter/common/base/BUILD
./pants -f src/java/com/twitter/common/base/BUILD:base

Note that the argument to the -f switch is composed of a path and an optional module name separated
by a colon.  The path portion of the argument need only point to the directory containing a BUILD
file, but it can also point directly at the BUILD file as well.  If no module name is specified,
the module with the same name as the BUILD file's enclosing directory is selected; otherwise the
specified module name is used.  If the module is not defined in the selected BUILD file pants will
report an error.

Currently pants uses ant to implement the tasks available for a module.  In practice this means
that pants will generate a build.xml/ivy.xml pair and pass any additional arguments along to ant."""

java_library(name = 'base',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'base',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:commons-lang',
    '3rdparty:guava',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/util:system-mocks',
  ],
  sources = globs('*.java'),
)
