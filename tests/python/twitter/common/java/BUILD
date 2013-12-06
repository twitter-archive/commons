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

python_library(
  name = 'resources',
  resources = globs('resources/*'),
)

python_test_suite(
  name = 'java',
  dependencies = [
    pants(':class_file'),
    pants(':perfdata'),
  ]
)

python_tests(
  name = 'class_file',
  sources = ['test_class_file.py'],
  dependencies = [
    pants('src/python/twitter/common/java'),
    pants(':resources'),
  ]
)

python_tests(
  name = 'perfdata',
  sources = ['test_perfdata.py'],
  dependencies = [
    pants('3rdparty/python:mock'),
    pants('src/python/twitter/common/java/perfdata'),
    pants(':resources'),
  ]
)
