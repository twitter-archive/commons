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

import sys
import copy
from collections import defaultdict
from functools import reduce

class DependencyCycle(Exception):
  pass

class UnderspecifiedDependencies(Exception):
  pass

def _preprocess_list(L):
  d = defaultdict(set)
  for elt in L:
    try:
      before, after = elt
    except Exception as e:
      raise TypeError('Expected a list of tuples, tried to unpack a %s' % type(elt))
    d[after].add(before)
  return dict(d)

def topological_sort(data, priors=[], require_fully_specified=False):
  """
    Topological sort.

    data can be one of two formats:
     (1) dictionary:
           after_dep => before_dep or set(before deps) or None<=>set()
     (2) list:
           sequence of (before, after) pairs of dependencies

    equivalent examples:
      (1) topological_sort({2: 1, 3: 2, 4: set([2, 3])})
      (2) topological_sort([ (1,2), (2,3), (2,4), (3,4) ])

    yields sets of elements as their dependencies are satisfied, or raises
    DependencyCycle exception.

    you may supply priors, an array of pre-satisfied dependencies.
  """
  data = copy.deepcopy(data)
  if isinstance(data, (list, tuple)):
    data = _preprocess_list(data)
  elif isinstance(data, dict):
    data = copy.deepcopy(data)
  else:
    raise TypeError('topological_sort must take a dictionary or a list, got %s' % type(data))

  # transform to dep => set(deps)
  for key, val in data.items():
    if val is None:
      data[key] = set()
    elif isinstance(val, str):
      data[key] = set([val])
    elif not hasattr(val, '__iter__'):
      data[key] = set([val])

  # filter self-references
  for key, val in data.items():
    val.discard(key)

  # keep track of unavailable dependencies
  unavailable_deps = reduce(set.union, data.values(), set()) - set(data.keys())
  unavailable_deps -= set(priors)
  if unavailable_deps and require_fully_specified:
    raise UnderspecifiedDependencies("Some dependencies unavailable: %s" %
      ' '.join(map(str, unavailable_deps)))
  data.update((key, set()) for key in unavailable_deps)

  def filter_keys(data, prior_set):
    return dict((key, values - prior_set) for key, values in data.items() if key not in prior_set)

  if priors:
    data = filter_keys(data, set(priors))
  while True:
    independent = set(after for (after, before) in data.items() if not before)
    if not independent:
      break
    else:
      yield independent
    data = filter_keys(data, independent)

  remaining_deps = reduce(set.union, data.values(), set())
  if remaining_deps:
    raise DependencyCycle('Data contained a cycle! Unsatisfied deps: %s' % remaining_deps)
