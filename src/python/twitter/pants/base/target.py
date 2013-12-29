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

from __future__ import print_function

import collections
import os

from twitter.common.collections import OrderedSet, maybe_list
from twitter.common.decorators import deprecated_with_warning

from twitter.pants.base.address import Address
from twitter.pants.base.hash_utils import hash_all
from twitter.pants.base.parse_context import ParseContext


class TargetDefinitionException(Exception):
  """Thrown on errors in target definitions."""
  def __init__(self, target, msg):
    Exception.__init__(self, 'Error in target %s: %s' % (target.address, msg))

class AbstractTarget(object):

  @property
  def is_concrete(self):
    """Returns true if a target resolves to itself."""
    targets = list(self.resolve())
    return len(targets) == 1 and targets[0] == self

  @property
  def has_resources(self):
    """Returns True if the target has an associated set of Resources."""
    return hasattr(self, 'resources') and self.resources

  @property
  def is_exported(self):
    """Returns True if the target provides an artifact exportable from the repo."""
    # TODO(John Sirois): fixup predicate dipping down into details here.
    return self.has_label('exportable') and self.provides

  @property
  def is_internal(self):
    """Returns True if the target is internal to the repo (ie: it might have dependencies)."""
    return self.has_label('internal')

  @property
  def is_jar(self):
    """Returns True if the target is a jar."""
    return False

  @property
  def is_jvm_app(self):
    """Returns True if the target produces a java application with bundled auxiliary files."""
    return False

  @property
  def is_thrift(self):
    """Returns True if the target has thrift IDL sources."""
    return False

  @property
  def is_jvm(self):
    """Returns True if the target produces jvm bytecode."""
    return self.has_label('jvm')

  @property
  def is_codegen(self):
    """Returns True if the target is a codegen target."""
    return self.has_label('codegen')

  @property
  def is_synthetic(self):
    """Returns True if the target is a synthetic target injected by the runtime."""
    return self.has_label('synthetic')

  @property
  def is_jar_library(self):
    """Returns True if the target is an external jar library."""
    return self.has_label('jars')

  @property
  def is_java(self):
    """Returns True if the target has or generates java sources."""
    return self.has_label('java')

  @property
  def is_apt(self):
    """Returns True if the target exports an annotation processor."""
    return self.has_label('apt')

  @property
  def is_python(self):
    """Returns True if the target has python sources."""
    return self.has_label('python')

  @property
  def is_scala(self):
    """Returns True if the target has scala sources."""
    return self.has_label('scala')

  @property
  def is_scalac_plugin(self):
    """Returns True if the target builds a scalac plugin."""
    return self.has_label('scalac_plugin')

  @property
  def is_test(self):
    """Returns True if the target is comprised of tests."""
    return self.has_label('tests')


class Target(AbstractTarget):
  """The baseclass for all pants targets.

  Handles registration of a target amongst all parsed targets as well as location of the target
  parse context.
  """

  _targets_by_address = {}
  _addresses_by_buildfile = collections.defaultdict(OrderedSet)

  @staticmethod
  def identify(targets):
    """Generates an id for a set of targets."""
    return Target.combine_ids(target.id for target in targets)

  @staticmethod
  def maybe_readable_identify(targets):
    """Generates an id for a set of targets.

    If the set is a single target, just use that target's id."""
    return Target.maybe_readable_combine_ids([target.id for target in targets])

  @staticmethod
  def combine_ids(ids):
    """Generates a combined id for a set of ids."""
    return hash_all(sorted(ids))  # We sort so that the id isn't sensitive to order.

  @staticmethod
  def maybe_readable_combine_ids(ids):
    """Generates combined id for a set of ids, but if the set is a single id, just use that."""
    ids = list(ids)  # We can't len a generator.
    return ids[0] if len(ids) == 1 else Target.combine_ids(ids)

  @staticmethod
  def get_all_addresses(buildfile):
    """Returns all of the target addresses in the specified buildfile if already parsed; otherwise,
    parses the buildfile to find all the addresses it contains and then returns them.
    """

    def lookup():
      if buildfile in Target._addresses_by_buildfile:
        return Target._addresses_by_buildfile[buildfile]
      else:
        return OrderedSet()

    addresses = lookup()
    if addresses:
      return addresses
    else:
      ParseContext(buildfile).parse()
      return lookup()

  @staticmethod
  def _clear_all_addresses():
    Target._targets_by_address = {}
    Target._addresses_by_buildfile = collections.defaultdict(OrderedSet)

  @staticmethod
  def get(address):
    """Returns the specified module target if already parsed; otherwise, parses the buildfile in the
    context of its parent directory and returns the parsed target.
    """

    def lookup():
      return Target._targets_by_address.get(address, None)

    target = lookup()
    if target:
      return target
    else:
      ParseContext(address.buildfile).parse()
      return lookup()

  @staticmethod
  def resolve_all(targets, *expected_types):
    """Yield the resolved concrete targets checking each is a subclass of one of the expected types
    if specified.
    """
    if targets:
      for target in maybe_list(targets, expected_type=Target):
        concrete_targets = [t for t in target.resolve() if t.is_concrete]
        for resolved in concrete_targets:
          if expected_types and not isinstance(resolved, expected_types):
            raise TypeError('Target requires types: %s and found %s' % (expected_types, resolved))
          yield resolved

  def __init__(self, name, reinit_check=True, exclusives=None):
    # See "get_all_exclusives" below for an explanation of the exclusives parameter.
    # This check prevents double-initialization in multiple-inheritance situations.
    # TODO(John Sirois): fix target inheritance - use super() to linearize or use alternatives to
    # multiple inheritance.
    if not reinit_check or not hasattr(self, '_initialized'):
      self.name = name
      self.description = None

      self.address = self.locate()

      # TODO(John Sirois): id is a builtin - use another name
      self.id = self._create_id()

      self.labels = set()
      self.register()
      self._initialized = True

      self.declared_exclusives = collections.defaultdict(set)
      if exclusives is not None:
        for k in exclusives:
          self.declared_exclusives[k].add(exclusives[k])
      self.exclusives = None

      # For synthetic codegen targets this will be the original target from which
      # the target was synthesized.
      self.derived_from = self

  def get_declared_exclusives(self):
    return self.declared_exclusives

  def add_to_exclusives(self, exclusives):
    if exclusives is not None:
      for key in exclusives:
        self.exclusives[key] |= exclusives[key]

  def get_all_exclusives(self):
    """ Get a map of all exclusives declarations in the transitive dependency graph.

    For a detailed description of the purpose and use of exclusives tags,
    see the documentation of the CheckExclusives task.

    """
    if self.exclusives is None:
      self._propagate_exclusives()
    return self.exclusives

  def _propagate_exclusives(self):
    if self.exclusives is None:
      self.exclusives = collections.defaultdict(set)
      self.add_to_exclusives(self.declared_exclusives)
      # This may perform more work than necessary.
      # We want to just traverse the immediate dependencies of this target,
      # but for a general target, we can't do that. _propagate_exclusives is overridden
      # in subclasses when possible to avoid the extra work.
      self.walk(lambda t: self._propagate_exclusives_work(t))

  def _propagate_exclusives_work(self, target):
    # Note: this will cause a stack overflow if there is a cycle in
    # the dependency graph, so exclusives checking should occur after
    # cycle detection.
    if hasattr(target, "declared_exclusives"):
      self.add_to_exclusives(target.declared_exclusives)
    return None

  def _post_construct(self, func, *args, **kwargs):
    """Registers a command to invoke after this target's BUILD file is parsed."""

    ParseContext.locate().on_context_exit(func, *args, **kwargs)

  def _create_id(self):
    """Generates a unique identifer for the BUILD target.  The generated id is safe for use as a
    a path name on unix systems.
    """

    buildfile_relpath = os.path.dirname(self.address.buildfile.relpath)
    if buildfile_relpath in ('.', ''):
      return self.name
    else:
      return "%s.%s" % (buildfile_relpath.replace(os.sep, '.'), self.name)

  def locate(self):
    parse_context = ParseContext.locate()
    return Address(parse_context.buildfile, self.name)

  def register(self):
    existing = Target._targets_by_address.get(self.address)
    if existing and existing.address.buildfile != self.address.buildfile:
      raise KeyError("%s defined in %s already defined in a sibling BUILD file: %s" % (
        self.address,
        self.address.buildfile.full_path,
        existing.address.buildfile.full_path,
      ))

    Target._targets_by_address[self.address] = self
    Target._addresses_by_buildfile[self.address.buildfile].add(self.address)

  def resolve(self):
    yield self

  def walk(self, work, predicate=None):
    """Performs a walk of this target's dependency graph visiting each node exactly once.  If a
    predicate is supplied it will be used to test each target before handing the target to work and
    descending.  Work can return targets in which case these will be added to the walk candidate set
    if not already walked.
    """

    self._walk(set(), work, predicate)

  def _walk(self, walked, work, predicate=None):
    for target in self.resolve():
      if target not in walked:
        walked.add(target)
        if not predicate or predicate(target):
          additional_targets = work(target)
          if hasattr(target, '_walk'):
            target._walk(walked, work, predicate)
          if additional_targets:
            for additional_target in additional_targets:
              if hasattr(additional_target, '_walk'):
                additional_target._walk(walked, work, predicate)

  # TODO(John Sirois): Kill this method once ant backend is gone
  @deprecated_with_warning("you're using deprecated pants commands, http://go/pantsmigration")
  def do_in_context(self, work):
    return ParseContext(self.address.buildfile).do_in_context(work)

  def with_description(self, description):
    self.description = description
    return self

  def add_labels(self, *label):
    self.labels.update(label)

  def remove_label(self, label):
    self.labels.remove(label)

  def has_label(self, label):
    return label in self.labels

  def __eq__(self, other):
    result = other and (
      type(self) == type(other)) and (
      self.address == other.address)
    return result

  def __hash__(self):
    return hash(self.address)

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "%s(%s)" % (type(self).__name__, self.address)

  @staticmethod
  def has_jvm_targets(targets):
    """Returns true if the given sequence of targets contains at least one jvm target as determined
    by is_jvm(...)
    """

    return len(list(Target.extract_jvm_targets(targets))) > 0

  @staticmethod
  def extract_jvm_targets(targets):
    """Returns an iterator over the jvm targets the given sequence of targets resolve to.  The
    given targets can be a mix of types and only valid jvm targets (as determined by is_jvm(...) 
    will be returned by the iterator.
    """

    for target in targets:
      if target is None:
        print('Warning! Null target!', file=sys.stderr)
        continue
      for real_target in target.resolve():
        if real_target.is_jvm:
          yield real_target

  def has_sources(self, extension=None):
    """Returns True if the target has sources.

    If an extension is supplied the target is further checked for at least 1 source with the given
    extension.
    """
    return (self.has_label('sources') and 
            (not extension or
             (hasattr(self, 'sources') and
              any(source.endswith(extension) for source in self.sources))))


Target._clear_all_addresses()
