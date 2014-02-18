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


def version_sources(version):
  specs = ['%s/%s' % (version, pattern) for pattern in ('css/*.css', 'js/*.js', 'img/*.png')]
  return globs(*specs)


resources(
  name='bootstrap',
  sources=version_sources('2.1.1') + version_sources('2.3.2')
)
