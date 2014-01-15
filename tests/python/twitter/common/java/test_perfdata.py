# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

import struct
import pkgutil

from twitter.common.java.perfdata import PerfData

import mock
import pytest


_EXAMPLE_RESOURCE = 'resources/example_hsperfdata'


def test_perfdata_integration():
  provider = lambda: pkgutil.get_data('twitter.common.java', _EXAMPLE_RESOURCE)
  perfdata = PerfData.get(provider)
  assert perfdata is not None
  perfdata.sample()

  assert len(perfdata) > 0
  keys = set(perfdata)
  for key in perfdata:
    assert key in keys
    assert perfdata[key] is not None


def test_struct_unpack_error():
  provider = lambda: pkgutil.get_data('twitter.common.java', _EXAMPLE_RESOURCE)
  perfdata = PerfData.get(provider)
  assert perfdata is not None

  with mock.patch('struct.unpack') as struct_unpack:
    struct_unpack.side_effect = struct.error('My shit got corrupted!')

    with pytest.raises(perfdata.ParseError):
      perfdata.sample()


def test_empty_ish_perfdata():
  provider = lambda: ''
  with pytest.raises(ValueError):
    perfdata = PerfData.get(provider)

  provider = lambda: PerfData.MAGIC
  with pytest.raises(ValueError):
    perfdata = PerfData.get(provider)
