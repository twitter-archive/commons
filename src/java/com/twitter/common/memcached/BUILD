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

from glob import glob
from pants import *

java_library(name = 'memcached',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:spy-memcached'),
  ],
  sources = [ 'Memcached.java' ]
)

java_library(name = 'thrift-transcoder',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:spy-memcached'),
    pants('3rdparty:thrift'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/io:thrift'),
  ],
  sources = [ 'ThriftTranscoder.java' ]
)
