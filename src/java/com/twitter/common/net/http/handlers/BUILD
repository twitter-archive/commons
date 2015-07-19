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

TEXT_SOURCES = ['TextResponseHandler.java']

STRING_TEMPLATE_SOURCES = ['StringTemplateServlet.java']

THRIFT_SOURCES = ['ThriftServlet.java']

TIME_SERIES_SOURCES = ['TimeSeriesDataSource.java']

PARAMS_SOURCES = ['HttpServletRequestParams.java']

java_library(
  name='text',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers-text',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/javax/servlet:servlet-api',
  ],
  sources=TEXT_SOURCES
)

java_library(
  name='string-template',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers-string-template',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/org/antlr:stringtemplate',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util/templating',
  ],
  sources=STRING_TEMPLATE_SOURCES
)

java_library(
  name='handlers',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/commons-codec',
    '3rdparty/jvm/com/google/code/gson',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/org/antlr:stringtemplate',
    ':params',
    ':string-template',
    ':text',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
  ],
  sources=globs('*.java',
                exclude=[TEXT_SOURCES,
                         STRING_TEMPLATE_SOURCES,
                         THRIFT_SOURCES,
                         TIME_SERIES_SOURCES,
                         PARAMS_SOURCES]),
  resources=[
    'src/resources/com/twitter/common/net/http/handlers:log'
  ]
)

java_library(
  name='thrift',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers-thrift',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/org/antlr:stringtemplate',
    'src/java/com/twitter/common/base',

    # TODO(John Sirois): the only need is for the non-existent thrift:monitoring
    'src/java/com/twitter/common/thrift',

    ':string-template',
  ],
  sources=THRIFT_SOURCES,
  resources=[
    'src/resources/com/twitter/common/net/http/handlers:thrift'
  ]
)

java_library(
  name='time-series',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers-time-series',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/code/gson',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/javax/servlet:servlet-api',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/stats:time-series',
  ],
  sources=TIME_SERIES_SOURCES
)

java_library(
  name='params',
  provides=artifact(
    org='com.twitter.common',
    name='net-http-handlers-params',
    repo=public,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/javax/servlet:servlet-api',
  ],
  sources=PARAMS_SOURCES
)
