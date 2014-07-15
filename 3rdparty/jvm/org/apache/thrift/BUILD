# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

jar_library(name='libthrift-0.5.0',
  jars=[
    jar(org='org.apache.thrift', name='libthrift', rev='0.5.0-1').with_sources(),
  ]
)

dependencies(name='thrift-0.5.0',
  dependencies=[
    pants('3rdparty/jvm/commons-lang'),
    pants(':libthrift-0.5.0'),
    pants('3rdparty/jvm/org/slf4j:slf4j-api'),
  ]
)

dependencies(name='thrift-0.5.0-finagle',
  dependencies=[
    pants(':thrift-0.5.0'),

    # finagle thrift extra deps
    pants('3rdparty/jvm/com/twitter:finagle-core'),
    pants('3rdparty/jvm/com/twitter:finagle-thrift'),
    pants('3rdparty/jvm/com/twitter:util-core'),
  ]
)

dependencies(name='thrift', dependencies=[pants(':thrift-0.5.0')])
