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

from twitter.common.collections import OrderedSet
from twitter.pants.base.target import Target

from .util import resolve


class TargetWithDependencies(Target):
  def __init__(self, name, dependencies=None, exclusives=None):
    Target.__init__(self, name, exclusives=exclusives)
    self.dependencies = OrderedSet(resolve(dependencies)) if dependencies else OrderedSet()

  def _walk(self, walked, work, predicate=None):
    Target._walk(self, walked, work, predicate)
    for dependency in self.dependencies:
      for dep in dependency.resolve():
        if isinstance(dep, Target) and not dep in walked:
          walked.add(dep)
          if not predicate or predicate(dep):
            additional_targets = work(dep)
            dep._walk(walked, work, predicate)
            if additional_targets:
              for additional_target in additional_targets:
                if hasattr(additional_target, '_walk'):
                  additional_target._walk(walked, work, predicate)
