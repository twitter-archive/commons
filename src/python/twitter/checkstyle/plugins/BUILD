# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

python_library(
  name = 'all',
  dependencies = [
    ':pep8',
    ':pyflakes',
    ':twitter',
  ]
)

python_library(
  name = 'plugin',
  sources = ['__init__.py'],
  dependencies = [
    'src/python/twitter/checkstyle:common',
  ]
)


python_requirement_library(
  name='pep8_module',
  requirements=[
    python_requirement(requirement='pep8==1.4.5'),
  ]
)

python_library(
  name = 'pep8',
  sources = ['pep8.py'],
  dependencies = [
    ':pep8_module',
    ':plugin',
    'src/python/twitter/checkstyle:common',
  ]
)

python_requirement_library(
  name='pyflakes_module',
  requirements=[
    python_requirement(requirement='pyflakes==0.7.2'),
  ]
)

python_library(
  name = 'pyflakes',
  sources = ['pyflakes.py'],
  dependencies = [
    ':plugin',
    ':pyflakes_module',
    'src/python/twitter/checkstyle:common',
  ]
)

python_library(
  name = 'twitter',
  sources = globs('*.py', exclude=['pep8.py', 'pyflakes.py']),
  dependencies = [
    ':plugin',
    'src/python/twitter/checkstyle:common',
  ]
)
