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

DYNAMIC_HOST_SET_SOURCES = ['pool/DynamicHostSet.java']

URL_RESOLVER_SOURCES = [
  'UrlResolver.java',
  'UrlResolverUtil.java'
]

java_library(name = 'dynamic-host-set',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'dynamic-host-set',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    'src/java/com/twitter/common/base',
  ],
  sources = DYNAMIC_HOST_SET_SOURCES
)

java_library(name = 'util',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-util',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/commons-codec',
    '3rdparty/jvm/commons-lang',
    '3rdparty/jvm/com/google/guava',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
  ],
  sources = globs('*.java', exclude = [URL_RESOLVER_SOURCES])
)

java_library(name = 'url-resolver',
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    ':util',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats:util',
    'src/java/com/twitter/common/util',
    'src/java/com/twitter/common/util/caching',
  ],
  sources = URL_RESOLVER_SOURCES
)

# TODO(John Sirois): tease apart loadbalancing/monitoring/pool
java_library(name = 'pool',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-pool',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/commons-codec',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    ':dynamic-host-set',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/util',
    'src/java/com/twitter/common/util:system-mocks',
  ],
  sources = globs('pool/*.java', 'monitoring/*.java', 'loadbalancing/*.java',
                  exclude = [DYNAMIC_HOST_SET_SOURCES])
)
