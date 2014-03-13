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
  name = "log",
  sources = rglobs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/dirutil'),
    pants('src/python/twitter/common/options'),
  ],
  provides = setup_py(
    name = 'twitter.common.log',
    version = '0.1.3',
    description = 'twitter.common logging handlers.',
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
  name = "log-scribe",
  dependencies = [
    pants(':log'),
    pants('src/thrift/org/apache/scribe:py-scribe'),
  ]
)
