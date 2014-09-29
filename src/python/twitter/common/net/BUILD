# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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
  name = 'tunnel',
  sources = ('tunnel.py',),
  dependencies = [
    'src/python/twitter/common/log',
    'src/python/twitter/common/quantity'
  ]
)


# aliases
python_library(
  name = 'net',
  dependencies = [
    ':tunnel',
  ],
  provides = setup_py(
    name = 'twitter.common.net',
    version = commons_version(),
    description = 'twitter.common tunneling and network utilities.',
    url = 'https://github.com/twitter/commons',
    license = 'Apache License, Version 2.0',
    zip_safe = True,
    classifiers = [
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: OS Independent',
      'Programming Language :: Python',
    ]
  )
)

python_library(
  name = 'ssh',
  dependencies = [
    ':tunnel',
  ]
)

python_library(
  name = 'socks',
  sources = ('socks.py',),
  dependencies = [
    '3rdparty/python:socksipy-branch'
  ]
)
