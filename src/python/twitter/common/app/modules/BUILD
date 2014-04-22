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
  name = "modules",
  sources = ['__init__.py'],
)

python_library(
  name = "all",
  dependencies = [
    pants(":exceptions"),
    pants(":http"),
    pants(":scribe_exceptions"),
    pants(":serverset"),
    pants(":vars"),
  ]
)

python_library(
  name = "exceptions",
  sources = ['exception_handler.py'],
  dependencies = [
    pants('src/python/twitter/common/exceptions')
  ],
)

python_library(
  name = "scribe_exceptions",
  sources = ['scribe_exception_handler.py'],
  dependencies = [
    pants('src/python/twitter/common/exceptions'),
    pants('src/thrift/org/apache/scribe:py-scribe'),
  ]
)

python_library(
  name = "http",
  sources = ['http.py'],
  dependencies = [
    pants('src/python/twitter/common/exceptions'),
    pants('src/python/twitter/common/http')
  ]
)

python_library(
  name = "serverset",
  sources = ['serverset.py'],
  dependencies = [
    pants('src/python/twitter/common/zookeeper/serverset')
  ]
)

python_library(
  name = "vars",
  sources = ['varz.py'],
  dependencies = [
    pants(':http'),
    pants('src/python/twitter/common/lang'),
    pants('src/python/twitter/common/metrics'),
    pants('src/python/twitter/common/quantity'),
  ]
)
