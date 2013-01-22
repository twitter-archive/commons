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

TEXT_SOURCES = [ 'TextResponseHandler.java' ]

STRING_TEMPLATE_SOURCES = [ 'StringTemplateServlet.java' ]

THRIFT_SOURCES = [ 'ThriftServlet.java' ]

TIME_SERIES_SOURCES = [ 'TimeSeriesDataSource.java' ]

java_library(name = 'text',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-text',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:servlet-api',
  ],
  sources = TEXT_SOURCES
)

java_library(name = 'string-template',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-string-template',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:servlet-api',
    '3rdparty:stringtemplate',
    'src/java/com/twitter/common/base',
  ],
  sources = STRING_TEMPLATE_SOURCES
)

java_library(name = 'handlers',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:commons-codec',
    '3rdparty:gson',
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:servlet-api',
    '3rdparty:stringtemplate',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
    ':string-template',
    ':text',
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
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    # TODO(John Sirois): the only need is for the non-existent thrift:monitoring
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:servlet-api',
    '3rdparty:stringtemplate',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/thrift',
    ':string-template',
  ],
  sources = THRIFT_SOURCES,
  resources = [ 'thrift.st' ]
)

java_library(name = 'time-series',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-time-series',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:servlet-api',
    '3rdparty:visualization-datasource',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/stats:time-series',
  ],
  sources = TIME_SERIES_SOURCES
)
