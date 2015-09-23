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

python_thrift_library(
  name='finagle-py-thrift',
  sources=remote_python_thrift_fileset(
    base_url='https://raw.github.com/twitter/finagle/master/finagle-thrift/src/main/thrift',
    sources=[
      ('tracing.thrift', 'gen.twitter.finagle.thrift')
    ]
  ),
  provides = setup_py(
    name = 'twitter.common.finagle-thrift',
    version = commons_version(),
    description = "twitter.common thrift stubs for zipkin rpc tracing support in finagle",
    url = 'https://github.com/twitter/commons',
    license = 'Apache License, Version 2.0',
    zip_safe = True,
    classifiers = [
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: OS Independent',
      'Programming Language :: Python',
    ]
  )
)
