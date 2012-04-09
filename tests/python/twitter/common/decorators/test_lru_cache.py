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

from twitter.common.decorators import lru_cache

def test_basic():
  # just test the extra functionality that we added

  eviction_queue = []
  def on_eviction(element):
    eviction_queue.append(element)

  @lru_cache(10, on_eviction=on_eviction)
  def double(value):
    return value * 2

  for k in range(15):
    double(k)

  assert eviction_queue == [0, 2, 4, 6, 8]
