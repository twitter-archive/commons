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


FINAL_VERSION = '0.7.0'


python_requirement_library(
  name = 'pex',
  requirements = [
    python_requirement('pex==%s' % FINAL_VERSION),
  ]
)


python_library(
  name = 'python-source',
  sources = globs('*.py', 'http/*.py'),
)


LONG_DESCRIPTION = """
As of 0.7.0, twitter.common.python has been renamed to pex.  Please install
pex instead.
"""


python_library(
  name = 'python',
  dependencies = [
    ':python-source',
    ':pex',
  ],
  provides = setup_py(
    name = 'twitter.common.python',
    version = FINAL_VERSION,
    description = "Twitter's Python packaging toolchain.",
    long_description = LONG_DESCRIPTION,
    url = 'https://github.com/twitter/commons/tree/master/src/python/twitter/common/python',
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
