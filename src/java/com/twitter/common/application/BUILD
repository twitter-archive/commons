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

ACTION_SOURCES = set([
    'ActionController.java',
    'ActionRegistry.java',
    'Lifecycle.java',
    'ShutdownStage.java',
    'StartupStage.java',
])

java_library(name = 'action',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application-action',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = ACTION_SOURCES
)

java_library(name = 'application',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'application',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('src/java/com/twitter/common/application/modules'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net/http/handlers'),
  ],
  sources = globs('*.java') - ACTION_SOURCES
)
