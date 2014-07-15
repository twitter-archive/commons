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

jar_library(name='tl4j',
  jars=[
    jar(org='com.google.testing', name='test-libraries-for-java', rev='1.1.1')
      # tl4j by default pulls in junit-4.6, which conflicts with 4.10 needed by our
      # testing code (e.g. ConcurrentJunitRunner). So exclude it here so that the junit
      # target defined above is used instead. tl4j seems to be happy with junit-4.10.
      .exclude(org='junit', name='junit')
  ]
)
