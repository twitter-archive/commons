# ==================================================================================================
# Copyright 2014 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================


from twitter.common.collections import DefaultOrderedDict


def test_default():
  a = DefaultOrderedDict()
  a['k1'] = 1
  assert a['k1'] == 1
  assert a['k2'] == None


def test_return_foo():
  b = DefaultOrderedDict().create(lambda: 'foo')
  b['k1'] = 1
  assert b['k1'] == 1
  assert b['k2'] == 'foo'

