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

from pants import *

TEXT_SOURCES = [ 'TextResponseHandler.java' ]

STRING_TEMPLATE_SOURCES = [ 'StringTemplateServlet.java' ]

THRIFT_SOURCES = [ 'ThriftServlet.java' ]

TIME_SERIES_SOURCES = [ 'TimeSeriesDataSource.java' ]

java_library(name = 'text',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-text',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:servlet-api')
  ],
  sources = TEXT_SOURCES
)

java_library(name = 'string-template',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-string-template',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:stringtemplate'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = STRING_TEMPLATE_SOURCES
)

java_library(name = 'handlers',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:commons-codec'),
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:stringtemplate'),
    pants(':text'),
    pants(':string-template'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/stats'),
  ],
  sources = globs('*.java')
    - TEXT_SOURCES - STRING_TEMPLATE_SOURCES - THRIFT_SOURCES - TIME_SERIES_SOURCES,

  resources = [
    'logconfig.st',
    'logprinter.st',
  ]
)

java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-thrift',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:stringtemplate'),
    pants(':string-template'),
    pants('src/java/com/twitter/common/base'),

    # TODO(John Sirois): the only need is for the non-existent thrift:monitoring
    pants('src/java/com/twitter/common/thrift'),
  ],
  sources = THRIFT_SOURCES,
  resources = [ 'thrift.st' ]
)

java_library(name = 'time-series',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-time-series',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:visualization-datasource'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/stats:time-series'),
  ],
  sources = TIME_SERIES_SOURCES
)
