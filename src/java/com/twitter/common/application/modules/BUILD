# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from pants import *

java_library(name = 'applauncher',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-applauncher',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = ['AppLauncherModule.java']
)

# TODO(William Farner): Split this up further - HTTP should not require thrit and time-series.
java_library(name = 'http',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-http',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/application/http'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net/http'),
    pants('src/java/com/twitter/common/net/http/handlers'),
    pants('src/java/com/twitter/common/net/http/handlers:thrift'),
    pants('src/java/com/twitter/common/net/http/handlers:time-series'),
  ],
  sources = ['HttpModule.java']
)

java_library(name = 'lifecycle',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-lifecycle',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application:action'),
  ],
  sources = ['LifecycleModule.java']
)

java_library(name = 'log',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-log',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/logging'),
    pants('src/java/com/twitter/common/net/http/handlers'),
    pants('src/java/com/twitter/common/stats:time-series'),
  ],
  sources = ['LogModule.java']
)

java_library(name = 'stats',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-stats',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/stats:jvm'),
    pants('src/java/com/twitter/common/stats:time-series'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = ['StatsModule.java']
)

java_library(name = 'stats_export',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-stats_export',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = ['StatsExportModule.java']
)

java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-thrift',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application/http'),
    pants('src/java/com/twitter/common/net/http'),
    pants('src/java/com/twitter/common/net/http/handlers:thrift'),
  ],
  sources = ['ThriftModule.java']
)
