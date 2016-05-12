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
#
# Dependencies for the open source Twitter Commons


# This consumes the requirements.txt in this directory and creates a python_requirement_library target for each entry.
python_requirements()


# A wrapper for requirements.txt entries whose names clash with BUILD file naming conventions.
def make_dep(name, version, dependency_name=None):
  """Build a target from a specified dependency tuple.

  :param str name: A target name, specified in other BUILD files.
  :param str version: A hardcoded version string.
  :param str dependency_name: Identifies the specific binary to resolve.
  """
  dependency_name = dependency_name or name
  versioned_name = "%s==%s" % (dependency_name, version)
  python_requirement_library(
    name=name,
    requirements=[
      python_requirement(requirement=versioned_name)
    ]
  )


make_dep('antlr-python-runtime', '3.1.3', 'antlr_python_runtime')
make_dep('git-python', '0.3.2.RC1', 'GitPython')
make_dep('socksipy-branch', '1.01', 'SocksiPy-Branch')
