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

from twitter.pants.base import ParseContext
from twitter.pants.base.target import Target, TargetDefinitionException
from twitter.pants.targets import InternalTarget
from twitter.pants.testutils import MockTarget
from twitter.pants.testutils.base_mock_target_test import BaseMockTargetTest


class InternalTargetTest(BaseMockTargetTest):

  def test_validation(self):
    with ParseContext.temp('InternalTargetTest/test_validation'):
      InternalTarget(name="valid", dependencies=None)
      self.assertRaises(TargetDefinitionException, InternalTarget,
                        name=1, dependencies=None)

      InternalTarget(name="valid2", dependencies=Target(name='mybird'))
      self.assertRaises(TargetDefinitionException, InternalTarget,
                        name='valid3', dependencies=1)

  def test_detect_cycle_direct(self):
    a = MockTarget('a')

    # no cycles yet
    InternalTarget.sort_targets([a])
    a.update_dependencies([a])
    try:
      InternalTarget.sort_targets([a])
      self.fail("Expected a cycle to be detected")
    except InternalTarget.CycleException:
      # expected
      pass

  def test_detect_cycle_indirect(self):
    c = MockTarget('c')
    b = MockTarget('b', [c])
    a = MockTarget('a', [c, b])

    # no cycles yet
    InternalTarget.sort_targets([a])

    c.update_dependencies([a])
    try:
      InternalTarget.sort_targets([a])
      self.fail("Expected a cycle to be detected")
    except InternalTarget.CycleException:
      # expected
      pass

  def testSort(self):
    a = MockTarget('a', [])
    b = MockTarget('b', [a])
    c = MockTarget('c', [b])
    d = MockTarget('d', [c, a])
    e = MockTarget('e', [d])

    self.assertEquals(InternalTarget.sort_targets([a,b,c,d,e]), [e,d,c,b,a])
    self.assertEquals(InternalTarget.sort_targets([b,d,a,e,c]), [e,d,c,b,a])
    self.assertEquals(InternalTarget.sort_targets([e,d,c,b,a]), [e,d,c,b,a])
