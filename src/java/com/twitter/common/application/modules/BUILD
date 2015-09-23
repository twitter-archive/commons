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

java_library(name = 'applauncher',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-applauncher',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util',
  ],
  sources = ['AppLauncherModule.java']
)

java_library(name = 'lifecycle',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-lifecycle',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/com/google/inject/extensions:guice-multibindings',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
  ],
  sources = ['LifecycleModule.java', 'LocalServiceRegistry.java']
)

# TODO(William Farner): Split this up further - HTTP should not require thrift and time-series.
java_library(name = 'http',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-http',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/inject:guice',
    ':lifecycle',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/application/http',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net/http',
    'src/java/com/twitter/common/net/http/handlers',
    'src/java/com/twitter/common/net/http/handlers:time-series',
    'src/java/com/twitter/common/net/http/handlers/pprof',
  ],
  sources = ['HttpModule.java']
)

java_library(name = 'log',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-log',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/logging',
    'src/java/com/twitter/common/net/http/handlers',
    'src/java/com/twitter/common/stats:time-series',
    ':lifecycle',
  ],
  sources = ['LogModule.java']
)

java_library(name = 'stats',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-stats',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats:jvm',
    'src/java/com/twitter/common/stats:time-series',
    'src/java/com/twitter/common/util',
    ':lifecycle',
  ],
  sources = ['StatsModule.java']
)

java_library(name = 'stats_export',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-stats_export',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
    ':lifecycle',
  ],
  sources = ['StatsExportModule.java']
)

java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-module-thrift',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/application/http',
    'src/java/com/twitter/common/net/http',
    'src/java/com/twitter/common/net/http/handlers:thrift',
  ],
  sources = ['ThriftModule.java']
)
