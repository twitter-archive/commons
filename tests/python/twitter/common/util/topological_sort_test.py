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

import unittest
import pytest
from twitter.common.util import (
  DependencyCycle,
  UnderspecifiedDependencies,
  topological_sort
)

class TopologicalSortTest(unittest.TestCase):
  def test_empty(self):
    assert list(topological_sort([])) == []
    assert list(topological_sort({})) == []

  def test_types(self):
    with pytest.raises(TypeError):
      list(topological_sort([1,2,3]))
    with pytest.raises(TypeError):
      list(topological_sort(None))
    with pytest.raises(TypeError):
      list(topological_sort((1,2), (2,3)))

  def test_basic_ordering(self):
    def run_asserts(output):
      assert output.pop(0) == set([1])
      assert output.pop(0) == set([2])
      assert output.pop(0) == set([3])

    output = list(topological_sort([(1,2), (2,3)]))
    run_asserts(output)
    output = list(topological_sort({2:1, 3:2}))
    run_asserts(output)

  def test_mixed_dict_sets(self):
    def run_asserts(output):
      assert output.pop(0) == set([1])
      assert output.pop(0) == set([2])
      assert output.pop(0) == set([3])
      assert output.pop(0) == set([4])
    output = list(topological_sort([(1,2), (2,3), (2,4), (3,4)]))
    run_asserts(output)
    output = list(topological_sort({2: 1, 3: 2, 4: set([2, 3])}))
    run_asserts(output)

  def test_mixed_types(self):
    deps = {
      1: "bob",
      2: "frank",
      "frank": "esther",
      "esther": set(["brian", 3]),
    }
    iter = topological_sort(deps)
    assert iter.next() == set(["bob", "brian", 3])
    assert iter.next() == set([1, "esther"])
    assert iter.next() == set(["frank"])
    assert iter.next() == set([2])

  def test_filtering(self):
    output = list(topological_sort([(1,1), (1,2)]))
    assert output.pop(0) == set([1])
    assert output.pop(0) == set([2])
    output = list(topological_sort({1: 1, 2: set([2,1])}))
    assert output.pop(0) == set([1])
    assert output.pop(0) == set([2])

  def test_cycles(self):
    with pytest.raises(DependencyCycle):
      list(topological_sort([(1,2), (2,1)]))
    with pytest.raises(DependencyCycle):
      list(topological_sort([(1,2), (2,3), (3,1)]))
    with pytest.raises(DependencyCycle):
      list(topological_sort({1: 2, 2: 3, 3: 1}))

  def test_unspecified_dependencies(self):
    with pytest.raises(UnderspecifiedDependencies):
      list(topological_sort([(1,2)], require_fully_specified=True))
    with pytest.raises(UnderspecifiedDependencies):
      list(topological_sort({2:1}, require_fully_specified=True))
    assert list(topological_sort({1:None, 2:1}, require_fully_specified=True)) == [
      set([1]), set([2])]

