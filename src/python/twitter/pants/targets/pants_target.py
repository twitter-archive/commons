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

from twitter.pants.base import ParseContext, Target, Address


class Pants(Target):
  """A pointer to a pants target."""

  def __init__(self, spec, exclusives=None):
    # it's critical the spec is parsed 1st, the results are needed elsewhere in constructor flow
    parse_context = ParseContext.locate()

    def parse_address():
      if spec.startswith(':'):
        # the :[target] could be in a sibling BUILD - so parse using the canonical address
        pathish = "%s:%s" % (parse_context.buildfile.canonical_relpath, spec[1:])
        return Address.parse(parse_context.buildfile.root_dir, pathish, False)
      else:
        return Address.parse(parse_context.buildfile.root_dir, spec, False)

    self.address = parse_address()

    # We must disable the re-init check, because our funky __getattr__ breaks it.
    # We're not involved in any multiple inheritance, so it's OK to disable it here.

    Target.__init__(self, self.address.target_name, reinit_check=False, exclusives=exclusives)

  def register(self):
    # A pants target is a pointer, do not register it as an actual target (see resolve).
    pass

  def locate(self):
    return self.address

  def resolve(self):
    # De-reference this pants pointer to an actual parsed target.
    resolved = Target.get(self.address)
    if not resolved:
      raise KeyError("Failed to find target for: %s" % self.address)
    for dep in resolved.resolve():
      yield dep

  def get(self):
    """De-reference this pants pointer to a single target.

    If the pointer aliases more than one target a LookupError is raised.
    """
    resolved = [t for t in self.resolve() if t.is_concrete]
    if len(resolved) > 1:
      raise LookupError('%s points to more than one target: %s' % (self, resolved))
    return resolved.pop()

  def __getattr__(self, name):
    try:
      return Target.__getattribute__(self, name)
    except AttributeError as e:
      try:
        return getattr(self.get(), name)
      except (AttributeError, LookupError):
        raise e
