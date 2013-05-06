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

import time 

from twitter.common.contextutil import Timer

def test_timer():
  with Timer() as t:
    assert t.start < time.time()
    assert t.elapsed > 0
    time.sleep(0.1)
    assert t.elapsed > 0.1
    time.sleep(0.1)
    assert t.finish is None
  assert t.elapsed > 0.2
  assert t.finish < time.time()

