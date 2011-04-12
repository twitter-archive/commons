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

java_library(name = 'zookeeper',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'zookeeper',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/io:thrift'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/net:dynamic-host-set'),
    pants('src/java/com/twitter/common/net:pool'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/util'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java'),
)

java_library(name = 'testing',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'zookeeper-testing',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:commons-io'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:tl4j'),
    pants('3rdparty:zookeeper'),
    pants(':zookeeper'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = globs('testing/*.java'),
)
