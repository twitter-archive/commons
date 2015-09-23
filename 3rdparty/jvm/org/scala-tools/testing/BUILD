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

jar_library(name='specs',
  jars=[
    # This arrangement gets our version of scalatest playing well with specs; without this,
    # a scala-library inconsistency - _despite_ matching 2.9.3 monikers at the dep roots - creeps
    # in.
    jar(org='org.scala-tools.testing', name='specs_2.10', rev='1.6.9')
      .exclude(org='org.scalatest', name='scalatest'),
  ],
  dependencies=[
    '3rdparty/jvm/org/scalatest'
  ]
)
