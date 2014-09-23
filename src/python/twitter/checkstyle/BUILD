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
  name = 'common',
  sources = ['common.py'],
  dependencies = [
    'src/python/twitter/common/lang',
  ]
)

python_library(
  name = 'iterators',
  sources = ['iterators.py'],
  dependencies = [
    '3rdparty/python:git-python',
    'src/python/twitter/common/dirutil',
  ]
)

python_library(
  name = 'checker',
  sources = ['checker.py'],
  dependencies = [
    ':common',
    ':iterators',
    'src/python/twitter/checkstyle/plugins:plugin',
    'src/python/twitter/common/app',
  ]
)

def make_checker(name, *plugins):
  python_binary(
    name = name,
    entry_point = 'twitter.checkstyle.checker:proxy_main',
    dependencies = [':checker'] + [
        'src/python/twitter/checkstyle/plugins:%s' % plugin for plugin in plugins
    ]
  )

make_checker('check_pep8', 'pep8')
make_checker('check_pyflakes', 'pyflakes')
make_checker('check_twitter', 'twitter')
make_checker('check', 'all')


python_library(
  name = 'checkstyle',
  dependencies = [
    ':checker',
  ],
  provides = setup_py(
    name = 'twitter.checkstyle',
    version = '0.1.0',
    description = "Twitter's python styleguide checkstyle tool.",
    url = 'https://github.com/twitter/commons/tree/master/src/python/twitter/checkstyle',
    license = 'Apache License, Version 2.0',
    zip_safe = True,
  ).with_binaries(
    twitterstyle = ':check',
  )
)
