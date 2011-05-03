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

from generator import TemplateData
from glob import glob1
from common.collections import OrderedSet

import collections
import os
import re

RESOURCES_BASE_DIR = 'src/resources'

class JarDependency(object):
  """Represents a binary jar dependency ala maven or ivy.  For the ivy dependency defined by:
    <dependency org="com.google.guava" name="guava" rev="r07"/>

  The equivalent Dependency object could be created with either of the following:
    Dependency(org = "com.google.guava", name = "guava", rev = "r07")
    Dependency("com.google.guava", "guava", "r07")

  If the rev keyword argument is left out, the revision is assumed to be the latest available."""

  def __init__(self, org, name, rev = None, ext = None):
    self.org = org
    self.name = name
    self.rev = rev
    self.excludes = []
    self.transitive = True
    self.ext = ext
    self._id = None
    self._configurations = [ 'default' ]

  def exclude(self, org, name = None):
    """Adds a transitive dependency of this jar to the exclude list."""

    self.excludes.append(Exclude(org, name))
    return self

  def intransitive(self):
    """Declares this Dependency intransitive, indicating only the jar for the depenency itself
    should be downloaded and placed on the classpath"""

    self.transitive = False
    return self

  def withSources(self):
    self._configurations.append('sources')
    return self

  def withDocs(self):
    self._configurations.append('docs')
    return self

  def __eq__(self, other):
    result = other and (
      type(other) == JarDependency) and (
      self.org == other.org) and (
      self.name == other.name) and (
      self.rev == other.rev)
    return result

  def __hash__(self):
    value = 17
    value *= 37 + hash(self.org)
    value *= 37 + hash(self.name)
    value *= 37 + hash(self.rev)
    return value

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "%s-%s-%s" % (self.org, self.name, self.rev)

  def resolve(self):
    yield self

  def _as_jar_dependencies(self):
    yield self

  def _create_template_data(self):
    return TemplateData(
      org = self.org,
      module = self.name,
      version = self.rev,
      excludes = self.excludes,
      transitive = self.transitive,
      ext = self.ext,
      configurations = ';'.join(self._configurations),
    )

class Exclude(object):
  """Represents a dependency exclude pattern to filter transitive dependencies against."""

  def __init__(self, org, name = None):
    self.org = org
    self.name = name

  def __eq__(self, other):
    return other and (
      type(other) == Exclude) and (
      self.org == other.org) and (
      self.name == other.name)

  def __hash__(self):
    value = 17
    value *= 37 + hash(self.org)
    value *= 37 + hash(self.name)
    return value

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "org=%s name=%s" % (self.org, self.name)

  def _create_template_data(self):
    return TemplateData(
      org = self.org,
      name = self.name,
    )

class BuildFile(object):
  _CANONICAL_NAME = 'BUILD'
  _PATTERN = re.compile('^%s(\.[a-z]+)?$' % _CANONICAL_NAME)

  @classmethod
  def _is_buildfile_name(cls, name):
    return BuildFile._PATTERN.match(name)

  @classmethod
  def scan_buildfiles(cls, root_dir, base_path = None):
    """Looks for all BUILD files under base_path"""

    buildfiles = OrderedSet()
    for root, dirs, files in os.walk(base_path if base_path else root_dir):
      for filename in files:
        if BuildFile._is_buildfile_name(filename):
          buildfile_relpath = os.path.relpath(os.path.join(root, filename), root_dir)
          buildfiles.add(BuildFile(root_dir, buildfile_relpath))
    return buildfiles

  def __init__(self, root_dir, relpath):
    """Creates a BuildFile object representing the BUILD file set at the specified path.

    root_dir: The base directory of the project
    relpath: The path relative to root_dir where the BUILD file is found - this can either point
        directly at the BUILD file or else to a directory which contains BUILD files
    raises IOError if the specified path does not house a BUILD file.
    """

    path = os.path.join(root_dir, relpath)
    buildfile = os.path.join(path, BuildFile._CANONICAL_NAME) if os.path.isdir(path) else path

    if not os.path.exists(buildfile):
      raise IOError("BUILD file does not exist at: %s" % (buildfile))

    if not BuildFile._is_buildfile_name(os.path.basename(buildfile)):
      raise IOError("%s is not a BUILD file" % buildfile)

    if os.path.isdir(buildfile):
      raise IOError("%s is a directory" % buildfile)

    if not os.path.exists(buildfile):
      raise IOError("BUILD file does not exist at: %s" % buildfile)

    self.root_dir = root_dir
    self.full_path = buildfile

    self.name = os.path.basename(self.full_path)
    self.parent_path = os.path.dirname(self.full_path)
    self.relpath = os.path.relpath(self.full_path, self.root_dir)
    self.canonical_relpath = os.path.join(os.path.dirname(self.relpath), BuildFile._CANONICAL_NAME)

  def family(self):
    """Returns an iterator over all the BUILD files co-located with this BUILD file.  The family
    forms a single logical BUILD file composed of the canonical BUILD file and optional sibling
    build files each with their own extension, eg: BUILD.extras."""

    yield self
    for build in glob1(self.parent_path, 'BUILD.*'):
      if self.name != build and BuildFile._is_buildfile_name(build):
        yield BuildFile(self.root_dir, os.path.join(os.path.dirname(self.relpath), build))

  def __eq__(self, other):
    result = other and (
      type(other) == BuildFile) and (
      self.full_path == other.full_path)
    return result

  def __hash__(self):
    return hash(self.full_path)

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return self.relpath

class ParseContext(object):
  """Defines the context of a parseable BUILD file target and provides a mechanism for targets to
  discover their context when invoked via eval."""

  _contexts = collections.deque([])

  @classmethod
  def locate(cls):
    """Attempts to find the current root directory and buildfile.  If there is an active parse
    context (see do_in_context), then it is returned."""

    return ParseContext._contexts[-1]

  def __init__(self, buildfile):
    self.buildfile = buildfile

  def parse(self):
    """The entrypoint to parsing of a BUILD file.  Changes the working directory to the BUILD file
    directory and then evaluates the BUILD file with the ROOT_DIR and __file__ globals set.  As
    target methods are parsed they can examine the stack to find these globals and thus locate
    themselves for the purposes of finding files (see locate() and bind())."""

    def _parse():
      start = os.path.abspath(os.curdir)
      try:
        os.chdir(self.buildfile.parent_path)
        for buildfile in self.buildfile.family():
          self.buildfile = buildfile
          eval_globals = { 'ROOT_DIR': buildfile.root_dir, '__file__': buildfile.full_path }
          execfile(buildfile.full_path, eval_globals, {})
      finally:
        os.chdir(start)

    self.do_in_context(_parse)

  def do_in_context(self, work):
    """Executes the callable work in this parse context."""

    try:
      ParseContext._contexts.append(self)
      return work()
    finally:
      ParseContext._contexts.pop()

class Address(object):
  """Represents a BUILD file target address."""

  _META_SUFFIX = '!'

  @classmethod
  def _parse_meta(cls, string):
    is_meta = string.endswith(Address._META_SUFFIX)
    parsed = string[:-1] if is_meta else string
    return parsed, is_meta

  @classmethod
  def parse(cls, root_dir, pathish, is_relative = True):
    """Parses pathish into an Address.  A pathish can be one of:
    1.) the (relative) path of a BUILD file
    2.) the (relative) path of a directory containing a BUILD file child
    3.) either of 1 or 2 with a ':[module name]' suffix
    4.) a bare ':[module name]' indicating the BUILD file to use is the one in the current directory

    If the pathish does not have a module suffix the targeted module name is taken to be the same
    name as the BUILD file's containing directory.  In this way the containing directory name
    becomes the 'default' module target for pants.

    If there is no BUILD file at the path pointed to, or if there is but the specified module target
    is not defined in the BUILD file, an IOError is raised."""

    parts = pathish.split(':') if not pathish.startswith(':') else [ '.', pathish[1:] ]
    path, is_meta = Address._parse_meta(parts[0])
    if is_relative:
      path = os.path.relpath(os.path.abspath(path), root_dir)
    buildfile = BuildFile(root_dir, path)

    if len(parts) == 1:
      parent_name = os.path.basename(os.path.dirname(buildfile.relpath))
      return Address(buildfile, parent_name, is_meta)
    else:
      target_name, is_meta = Address._parse_meta(':'.join(parts[1:]))
      return Address(buildfile, target_name, is_meta)

  def __init__(self, buildfile, target_name, is_meta):
    self.buildfile = buildfile
    self.target_name = target_name
    self.is_meta = is_meta

  def __eq__(self, other):
    result = other and (
      type(other) == Address) and (
      self.buildfile.canonical_relpath == other.buildfile.canonical_relpath) and (
      self.target_name == other.target_name)
    return result

  def __hash__(self):
    value = 17
    value *= 37 + hash(self.buildfile.canonical_relpath)
    value *= 37 + hash(self.target_name)
    return value

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "%s:%s%s" % (
      self.buildfile,
      self.target_name,
      Address._META_SUFFIX if self.is_meta else ''
    )

class Target(object):
  """The baseclass for all pants targets.  Handles registration of a target amongst all parsed
  targets as well as location of the target parse context."""

  _targets_by_address = {}
  _addresses_by_buildfile = collections.defaultdict(OrderedSet)

  @classmethod
  def get_all_addresses(cls, buildfile):
    """Returns all of the target addresses in the specified buildfile if already parsed; otherwise,
    parses the buildfile to find all the addresses it contains and then returns them."""

    def lookup():
      if buildfile in Target._addresses_by_buildfile:
        return Target._addresses_by_buildfile[buildfile]
      else:
        return None

    addresses = lookup()
    if addresses:
      return addresses
    else:
      ParseContext(buildfile).parse()
      return lookup()

  @classmethod
  def get(cls, address):
    """Returns the specified module target if already parsed; otherwise, parses the buildfile in the
    context of its parent directory and returns the parsed target."""

    def lookup():
      return Target._targets_by_address[address] if address in Target._targets_by_address else None

    target = lookup()
    if target:
      return target
    else:
      ParseContext(address.buildfile).parse()
      return lookup()

  def __init__(self, name, is_meta):
    object.__init__(self)

    self.name = name
    self.is_meta = is_meta
    self.is_codegen = False

    self.address = self.locate()
    self._id = self._create_id()
    self.register()

  def _create_id(self):
    """Generates a unique identifer for the BUILD target.  The generated id is safe for use as a
    a path name on unix systems."""

    buildfile_relpath = os.path.dirname(self.address.buildfile.relpath)
    if buildfile_relpath is '.':
      return self.name
    else:
      return "%s.%s" % (buildfile_relpath.replace(os.sep, '.'), self.name)

  def locate(self):
    parse_context = ParseContext.locate()
    return Address(parse_context.buildfile, self.name, self.is_meta)

  def register(self):
    existing = Target._targets_by_address.get(self.address)
    if existing and existing.address.buildfile != self.address.buildfile:
      raise KeyError("%s already defined in a sibling BUILD file: %s" % (
        self.address,
        existing.address,
      ))

    Target._targets_by_address[self.address] = self
    Target._addresses_by_buildfile[self.address.buildfile].add(self.address)

  def resolve(self):
    yield self

  def walk(self, work, predicate = None):
    """Performs a walk of this target's dependency graph visiting each node exactly once.  If a
    predicate is supplied it will be used to test each target before handing the target to work and
    descending.  Work can return targets in which case these will be added to the walk candidate set
    if not already walked."""

    self._walk(set(), work, predicate)

  def _walk(self, walked, work, predicate = None):
    for target in self.resolve():
      if target not in walked:
        walked.add(target)
        if not predicate or predicate(target):
          additional_targets = work(target)
          target._walk(walked, work, predicate)
          if additional_targets:
            for additional_target in additional_targets:
              additional_target._walk(walked, work, predicate)

  def do_in_context(self, work):
    return ParseContext(self.address.buildfile).do_in_context(work)

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

class Pants(Target):
  """A pointer to a pants target."""

  def __init__(self, spec):
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

    Target.__init__(self, self.address.target_name, False)

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

class JarLibrary(Target):
  """Serves as a proxy for one or more JarDependencies or JavaTargets."""

  def __init__(self, name, *dependencies):
    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    dependencies: one or more JarDependencies this JarLibrary bundles or Pants pointing to other
        JarLibraries or JavaTargets"""

    assert len(dependencies) > 0, "At least one dependency must be specified"
    Target.__init__(self, name, False)

    self.dependencies = dependencies

  def resolve(self):
    for dependency in self.dependencies:
      for resolved_dependency in dependency.resolve():
        yield resolved_dependency

class Repository(Target):
  """Represents an artifact repository.  Typically this is a maven-style artifact repo."""

  def __init__(self, name, url, push_db):
    """name: an identifier for the repo
    url: the url used to access the repo and retrieve artifacts or artifact metadata
    push_db: the data file associated with this repo that records artifact push history"""

    Target.__init__(self, name, False)

    self.name = name
    self.url = url
    self.push_db = push_db

  def __eq__(self, other):
    result = other and (
      type(other) == Repository) and (
      self.name == other.name)
    return result

  def __hash__(self):
    return hash(self.name)

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "%s -> %s (%s)" % (self.name, self.url, self.push_db)

class Artifact(object):
  """Represents a jvm artifact ala maven or ivy."""

  def __init__(self, org, name, repo):
    """org: the originization of this artifact, the group id in maven parlance
    name: the name of the artifact
    repo: the repository this artifact is published to"""

    self.org = org
    self.name = name
    self.rev = None
    repos = list(repo.resolve())
    if len(repos) != 1:
      raise Exception("An artifact must have exactly 1 repo, given: %s" % repos)
    self.repo = repos[0]

  def __eq__(self, other):
    result = other and (
      type(other) == Artifact) and (
      self.org == other.org) and (
      self.name == other.name)
    return result

  def __hash__(self):
    value = 17
    value *= 37 + hash(self.org)
    value *= 37 + hash(self.name)
    return value

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "%s-%s -> %s" % (self.org, self.name, self.repo)

  def _create_template_data(self):
    return TemplateData(
      org = self.org,
      module = self.name,
      version = self.rev,
      repo = self.repo.name
    )

class CycleException(Exception):
  """Thrown when a circular dependency is detected."""

  def __init__(self, precedents, cycle):
    Exception.__init__(self, 'Cycle detected along path:\n\t%s' % (
      ' ->\n\t'.join(str(target.address) for target in list(precedents) + [ cycle ])
    ))

class InternalTarget(Target):
  """A baseclass for targets that support an optional dependency set."""

  @classmethod
  def check_cycles(cls, internal_target):
    """Validates the given InternalTarget has no circular dependencies.  Raises CycleException if
    it does."""

    dep_stack = OrderedSet()

    def descend(internal_dep):
      if internal_dep in dep_stack:
        raise CycleException(dep_stack, internal_dep)
      if hasattr(internal_dep, 'internal_dependencies'):
        dep_stack.add(internal_dep)
        for dep in internal_dep.internal_dependencies:
          descend(dep)
        dep_stack.remove(internal_dep)

    descend(internal_target)

  @classmethod
  def sort_targets(cls, internal_targets):
    """Returns a list of targets that internal_targets depend on sorted from most dependent to
    least."""

    roots = OrderedSet()
    inverted_deps = collections.defaultdict(OrderedSet) # target -> dependent targets
    visited = set()

    def invert(target):
      if target not in visited:
        visited.add(target)
        if target.internal_dependencies:
          for internal_dependency in target.internal_dependencies:
            if isinstance(internal_dependency, InternalTarget):
              inverted_deps[internal_dependency].add(target)
              invert(internal_dependency)
        else:
          roots.add(target)

    for internal_target in internal_targets:
      invert(internal_target)

    sorted = []
    visited.clear()

    def topological_sort(target):
      if target not in visited:
        visited.add(target)
        if target in inverted_deps:
          for dep in inverted_deps[target]:
            topological_sort(dep)
        sorted.append(target)

    for root in roots:
      topological_sort(root)

    return sorted

  @classmethod
  def coalesce_targets(cls, internal_targets):
    """Returns a list of targets internal_targets depend on sorted from most dependent to least and
    grouped where possible by target type."""

    sorted_targets = InternalTarget.sort_targets(internal_targets)

    # can do no better for any of these:
    # []
    # [a]
    # [a,b]
    if len(sorted_targets) <= 2:
      return sorted_targets

    # For these, we'd like to coalesce if possible, like:
    # [a,b,a,c,a,c] -> [a,a,a,b,c,c]
    # adopt a quadratic worst case solution, when we find a type change edge, scan forward for
    # the opposite edge and then try to swap dependency pairs to move the type back left to its
    # grouping.  If the leftwards migration fails due to a dependency constraint, we just stop
    # and move on leaving "type islands".
    current_type = None

    # main scan left to right no backtracking
    for i in range(len(sorted_targets) - 1):
      current_target = sorted_targets[i]
      if current_type != type(current_target):
        scanned_back = False

        # scan ahead for next type match
        for j in range(i + 1, len(sorted_targets)):
          look_ahead_target = sorted_targets[j]
          if current_type == type(look_ahead_target):
            scanned_back = True

            # swap this guy as far back as we can
            for k in range(j, i, -1):
              previous_target = sorted_targets[k - 1]
              mismatching_types = current_type != type(previous_target)
              not_a_dependency = look_ahead_target not in previous_target.internal_dependencies
              if mismatching_types and not_a_dependency:
                sorted_targets[k] = sorted_targets[k - 1]
                sorted_targets[k - 1] = look_ahead_target
              else:
                break # out of k

            break # out of j

        if not scanned_back: # done with coalescing the current type, move on to next
          current_type = type(current_target)

    return sorted_targets

  def sort(self):
    """Returns a list of targets this target depends on sorted from most dependent to least."""

    return InternalTarget.sort_targets([ self ])

  def coalesce(self):
    """Returns a list of targets this target depends on sorted from most dependent to least and
    grouped where possible by target type."""

    return InternalTarget.coalesce_targets([ self ])

  def __init__(self, name, dependencies, is_meta):
    Target.__init__(self, name, is_meta)

    self.resolved_dependencies = OrderedSet()
    self.internal_dependencies = OrderedSet()
    self.jar_dependencies = OrderedSet()

    self.update_dependencies(dependencies)

  def update_dependencies(self, dependencies):
    if dependencies:
      for dependency in dependencies:
        for resolved_dependency in dependency.resolve():
          self.resolved_dependencies.add(resolved_dependency)
          if isinstance(resolved_dependency, InternalTarget):
            self.internal_dependencies.add(resolved_dependency)
          self.jar_dependencies.update(resolved_dependency._as_jar_dependencies())

  def _walk(self, walked, work, predicate = None):
    Target._walk(self, walked, work, predicate)
    for dep in self.resolved_dependencies:
      if isinstance(dep, Target) and not dep in walked:
        walked.add(dep)
        if not predicate or predicate(dep):
          additional_targets = work(dep)
          dep._walk(walked, work, predicate)
          if additional_targets:
            for additional_target in additional_targets:
              additional_target._walk(walked, work, predicate)

class TargetWithSources(Target):
  def __init__(self, target_base, name, is_meta = False):
    Target.__init__(self, name, is_meta)

    self.target_base = target_base

  def _resolve_paths(self, base, paths):
    # meta targets are composed of already-resolved paths
    if not paths or self.is_meta:
      return paths

    def flatten_paths(*items):
      """Flattens one or more items into a list.  If the item is iterable each of its items is
      flattened.  If an item is callable, it is called and the result is flattened.  Otherwise the
      atom is appended to the flattened list.  These rules are applied recursively such that the
      returned list will only contain non-iterable, non-callable atoms."""

      flat = []

      def flatmap(item):
        if isinstance(item, basestring):
          flat.append(item)
        else:
          try:
            for i in iter(item):
              flatmap(i)
          except:
            if callable(item):
              flatmap(item())
            else:
              flat.append(item)

      for item in items:
        flatmap(item)

      return flat

    base_path = os.path.join(self.address.buildfile.root_dir, self.target_base)
    buildfile = os.path.join(self.address.buildfile.root_dir, self.address.buildfile.relpath)
    src_relpath = os.path.dirname(buildfile).replace(base_path + '/', '')

    src_root = os.path.join(self.address.buildfile.root_dir, base)
    src_base_path = os.path.join(src_root, src_relpath)

    def resolve_path(path):
      if path.startswith('/'):
        return path[1:]
      else:
        return os.path.join(src_relpath, path)

    start = os.path.abspath(os.curdir)
    try:
      os.chdir(src_base_path)
      return [ resolve_path(path) for path in flatten_paths(paths) ]
    finally:
      os.chdir(start)

class JvmTarget(InternalTarget, TargetWithSources):
  """A base class for all java module targets that provides path and dependency translation."""

  def __init__(self, target_base, name, sources, dependencies, excludes = None,
               buildflags = None, is_meta = False):
    InternalTarget.__init__(self, name, dependencies, is_meta)
    TargetWithSources.__init__(self, target_base, name, is_meta)

    self.sources = self._resolve_paths(target_base, sources)
    self.excludes = excludes
    self.buildflags = buildflags

    custom_antxml = '%s.xml' % self.name
    buildfile = self.address.buildfile.full_path
    custom_antxml_path = os.path.join(os.path.dirname(buildfile), custom_antxml)
    self.custom_antxml_path = custom_antxml_path if os.path.exists(custom_antxml_path) else None

  def _as_jar_dependency(self):
    jar_dependency, _, _ = self._get_artifact_info()
    jar = JarDependency(org = jar_dependency.org, name = jar_dependency.name, rev = None)
    jar._id = self._id
    return jar

  def _as_jar_dependencies(self):
    yield self._as_jar_dependency()

  def _get_artifact_info(self):
    provides = self._provides()
    exported = bool(provides)

    org = provides.org if exported else 'internal'
    module = provides.name if exported else self._id
    version = provides.rev if exported else None

    id = "%s-%s" % (provides.org, provides.name) if exported else self._id

    return JarDependency(org = org, name = module, rev = version), id, exported

  def _provides(self):
    return None

class ExportableJvmLibrary(JvmTarget):
  """A baseclass for java targets that support being exported to an artifact repository."""

  def __init__(self,
               target_base,
               name,
               sources,
               provides = None,
               dependencies = None,
               excludes = None,
               buildflags = None,
               is_meta = False):

    # it's critical provides is set 1st since _provides() is called elsewhere in the constructor
    # flow
    self.provides = provides

    JvmTarget.__init__(self,
                        target_base,
                        name,
                        sources,
                        dependencies,
                        excludes,
                        buildflags,
                        is_meta)

  def _provides(self):
    return self.provides


  def _create_template_data(self):
    jar_dependency, id, exported = self._get_artifact_info()

    if self.excludes:
      exclude_template_datas = [exclude._create_template_data() for exclude in self.excludes]
    else:
      exclude_template_datas = None

    return TemplateData(
      id = id,
      name = self.name,
      template_base = self.target_base,
      exported = exported,
      org = jar_dependency.org,
      module = jar_dependency.name,
      version = jar_dependency.rev,
      sources = self.sources,
      dependencies = [dep._create_template_data() for dep in self.jar_dependencies],
      excludes = exclude_template_datas,
      buildflags = self.buildflags,
      publish_properties = self.provides.repo.push_db if exported else None,
      publish_repo = self.provides.repo.name if exported else None,
    )

class JavaThriftLibrary(ExportableJvmLibrary):
  """Defines a target that builds java stubs from a thrift IDL file."""

  _SRC_DIR = 'src/thrift'

  @classmethod
  def _aggregate(cls, name, provides, buildflags, java_thrift_libs):
    all_sources = []
    all_deps = OrderedSet()
    all_excludes = OrderedSet()

    for java_thrift_lib in java_thrift_libs:
      if java_thrift_lib.sources:
        all_sources.extend(java_thrift_lib.sources)
      if java_thrift_lib.resolved_dependencies:
        all_deps.update(dep for dep in java_thrift_lib.jar_dependencies if dep.rev is not None)
      if java_thrift_lib.excludes:
        all_excludes.update(java_thrift_lib.excludes)

    return JavaThriftLibrary(name,
                             all_sources,
                             provides = provides,
                             dependencies = all_deps,
                             excludes = all_excludes,
                             buildflags = buildflags,
                             is_meta = True)

  def __init__(self,
               name,
               sources,
               provides = None,
               dependencies = None,
               excludes = None,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the thrift source files this module's jar is compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    def get_all_deps():
      all_deps = OrderedSet()
      all_deps.update(Pants('3rdparty:commons-lang').resolve())
      all_deps.update(JarDependency(org = 'org.apache.thrift',
                                    name = 'libthrift',
                                    rev = '${thrift.library.version}').resolve())
      all_deps.update(Pants('3rdparty:slf4j-api').resolve())
      if dependencies:
        all_deps.update(dependencies)
      return all_deps

    ExportableJvmLibrary.__init__(self,
                                   JavaThriftLibrary._SRC_DIR,
                                   name,
                                   sources,
                                   provides,
                                   get_all_deps(),
                                   excludes,
                                   buildflags,
                                   is_meta)
    self.is_codegen = True

  def _as_jar_dependency(self):
    return ExportableJvmLibrary._as_jar_dependency(self).withSources()

  def _create_template_data(self):
    allsources = []
    if self.sources:
      allsources += list(os.path.join(JavaThriftLibrary._SRC_DIR, src) for src in self.sources)

    return ExportableJvmLibrary._create_template_data(self).extend(
      allsources = allsources,
    )

class JavaProtobufLibrary(ExportableJvmLibrary):
  """Defines a target that builds java stubs from a protobuf IDL file."""

  _SRC_DIR = 'src/protobuf'

  @classmethod
  def _aggregate(cls, name, provides, buildflags, java_proto_libs):
    all_sources = []
    all_deps = OrderedSet()
    all_excludes = OrderedSet()

    for java_proto_lib in java_proto_libs:
      if java_proto_lib.sources:
        all_sources.extend(java_proto_lib.sources)
      if java_proto_lib.resolved_dependencies:
        all_deps.update(dep for dep in java_proto_lib.jar_dependencies if dep.rev is not None)
      if java_proto_lib.excludes:
        all_excludes.update(java_proto_lib.excludes)

    return JavaProtobufLibrary(name,
                               all_sources,
                               provides = provides,
                               dependencies = all_deps,
                               excludes = all_excludes,
                               buildflags = buildflags,
                               is_meta = True)

  def __init__(self,
               name,
               sources,
               provides = None,
               dependencies = None,
               excludes = None,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the protobuf source files this modules jar is compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    def get_all_deps():
      all_deps = set([
        JarDependency(org = 'com.google.protobuf',
                      name = 'protobuf-java',
                      rev = '${protobuf.library.version}'),
      ])
      if dependencies:
        all_deps.update(dependencies)
      return all_deps

    ExportableJvmLibrary.__init__(self,
                                   JavaProtobufLibrary._SRC_DIR,
                                   name,
                                   sources,
                                   provides,
                                   get_all_deps(),
                                   excludes,
                                   buildflags,
                                   is_meta)
    self.is_codegen = True

  def _as_jar_dependency(self):
    return ExportableJvmLibrary._as_jar_dependency(self).withSources()

  def _create_template_data(self):
    allsources = []
    if self.sources:
      allsources += list(os.path.join(JavaProtobufLibrary._SRC_DIR, src) for src in self.sources)

    return ExportableJvmLibrary._create_template_data(self).extend(
      allsources = allsources,
    )

class JavaLibrary(ExportableJvmLibrary):
  """Defines a target that produces a java library."""

  _SRC_DIR = 'src/java'

  @classmethod
  def _aggregate(cls, name, provides, deployjar, buildflags, java_libs):
    all_deps = OrderedSet()
    all_excludes = OrderedSet()
    all_sources = []
    all_resources = []
    all_binary_resources = []

    for java_lib in java_libs:
      if java_lib.resolved_dependencies:
        all_deps.update(dep for dep in java_lib.jar_dependencies if dep.rev is not None)
      if java_lib.excludes:
        all_excludes.update(java_lib.excludes)
      if java_lib.sources:
        all_sources.extend(java_lib.sources)
      if java_lib.resources:
        all_resources.extend(java_lib.resources)
      if java_lib.binary_resources:
        all_binary_resources.extend(java_lib.binary_resources)

    return JavaLibrary(name,
                       all_sources,
                       provides = provides,
                       dependencies = all_deps,
                       excludes = all_excludes,
                       resources = all_resources,
                       binary_resources = all_binary_resources,
                       deployjar = deployjar,
                       buildflags = buildflags,
                       is_meta = True)

  def __init__(self, name, sources,
               provides = None,
               dependencies = None,
               excludes = None,
               resources = None,
               binary_resources = None,
               deployjar = False,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the java source files this modules jar is compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    resources: An optional list of paths containing (filterable) text file resources to place in
        this module's jar
    binary_resources: An optional list of paths containing binary resources to place in this
        module's jar
    deployjar: An optional boolean that turns on generation of a monolithic deploy jar
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    ExportableJvmLibrary.__init__(self,
                                  JavaLibrary._SRC_DIR,
                                  name,
                                  sources,
                                  provides,
                                  dependencies,
                                  excludes,
                                  buildflags,
                                  is_meta)

    self.resources = self._resolve_paths(RESOURCES_BASE_DIR, resources)
    self.binary_resources = self._resolve_paths(RESOURCES_BASE_DIR, binary_resources)
    self.deployjar = deployjar

  def _create_template_data(self):
    allsources = []
    if self.sources:
      allsources += list(os.path.join(JavaLibrary._SRC_DIR, source) for source in self.sources)
    if self.resources:
      allsources += list(os.path.join(RESOURCES_BASE_DIR, res) for res in self.resources)
    if self.binary_resources:
      allsources += list(os.path.join(RESOURCES_BASE_DIR, res) for res in self.binary_resources)

    return ExportableJvmLibrary._create_template_data(self).extend(
      resources = self.resources,
      binary_resources = self.binary_resources,
      deploy_jar = self.deployjar,
      allsources = allsources
    )

class JavaTests(JvmTarget):
  """Defines a target that tests a java library."""

  @classmethod
  def _aggregate(cls, name, buildflags, java_tests):
    all_deps = OrderedSet()
    all_excludes = OrderedSet()
    all_sources = []

    for java_test in java_tests:
      if java_test.resolved_dependencies:
        all_deps.update(dep for dep in java_test.jar_dependencies if dep.rev is not None)
      if java_test.excludes:
        all_excludes.update(java_test.excludes)
      if java_test.sources:
        all_sources.extend(java_test.sources)

    return JavaTests(name,
                     all_sources,
                     dependencies = all_deps,
                     excludes = all_excludes,
                     buildflags = buildflags,
                     is_meta = True)

  def __init__(self,
               name,
               sources,
               dependencies = None,
               excludes = None,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the java source files this modules tests are compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    def get_all_deps():
      all_deps = OrderedSet()
      all_deps.update(Pants('3rdparty:junit').resolve())
      if dependencies:
        all_deps.update(dependencies)
      return all_deps

    JvmTarget.__init__(self,
                        'tests/java',
                        name,
                        sources,
                        get_all_deps(),
                        excludes,
                        buildflags,
                        is_meta)

  def _create_template_data(self):
    jar_dependency, id, exported = self._get_artifact_info()

    if self.excludes:
      exclude_template_datas = [exclude._create_template_data() for exclude in self.excludes]
    else:
      exclude_template_datas = None

    return TemplateData(
      id = id,
      name = self.name,
      template_base = self.target_base,
      exported = exported,
      org = jar_dependency.org,
      module = jar_dependency.name,
      version = jar_dependency.rev,
      sources = self.sources,
      dependencies = [dep._create_template_data() for dep in self.jar_dependencies],
      excludes = exclude_template_datas,
      buildflags = self.buildflags,
    )

class ScalaLibrary(ExportableJvmLibrary):
  """Defines a target that produces a scala library."""

  _SRC_DIR = 'src/scala'

  @classmethod
  def _aggregate(cls, name, provides, deployjar, buildflags, scala_libs):
    all_deps = OrderedSet()
    all_excludes = OrderedSet()
    all_sources = []
    all_java_sources = []
    all_resources = []
    all_binary_resources = []

    for scala_lib in scala_libs:
      if scala_lib.resolved_dependencies:
        all_deps.update(dep for dep in scala_lib.jar_dependencies if dep.rev is not None)
      if scala_lib.excludes:
        all_excludes.update(scala_lib.excludes)
      if scala_lib.sources:
        all_sources.extend(scala_lib.sources)
      if scala_lib.java_sources:
        all_java_sources.extend(scala_lib.java_sources)
      if scala_lib.resources:
        all_resources.extend(scala_lib.resources)
      if scala_lib.binary_resources:
        all_binary_resources.extend(scala_lib.binary_resources)

    return ScalaLibrary(name,
                        all_sources,
                        java_sources = all_java_sources,
                        provides = provides,
                        dependencies = all_deps,
                        excludes = all_excludes,
                        resources = all_resources,
                        binary_resources = all_binary_resources,
                        deployjar = deployjar,
                        buildflags = buildflags,
                        is_meta = True)

  def __init__(self, name, sources,
               java_sources = None,
               provides = None,
               dependencies = None,
               excludes = None,
               resources = None,
               binary_resources = None,
               deployjar = False,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the scala source files this module's jar is compiled from
    java_sources: An optional list of paths containing the java sources this module's jar is in part
        compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    resources: An optional list of paths containing (filterable) text file resources to place in
        this module's jar
    binary_resources: An optional list of paths containing binary resources to place in this
        module's jar
    deployjar: An optional boolean that turns on generation of a monolithic deploy jar
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    def get_all_deps():
      all_deps = OrderedSet()
      all_deps.update(Pants('3rdparty:scala-library').resolve())
      if dependencies:
        all_deps.update(dependencies)
      return all_deps

    ExportableJvmLibrary.__init__(self,
                                  ScalaLibrary._SRC_DIR,
                                  name,
                                  sources,
                                  provides,
                                  get_all_deps(),
                                  excludes,
                                  buildflags,
                                  is_meta)

    self.java_sources = self._resolve_paths('src/java', java_sources)
    self.resources = self._resolve_paths(RESOURCES_BASE_DIR, resources)
    self.binary_resources = self._resolve_paths(RESOURCES_BASE_DIR, binary_resources)
    self.deployjar = deployjar

  def _create_template_data(self):
    allsources = []
    if self.sources:
      allsources += list(os.path.join(ScalaLibrary._SRC_DIR, source) for source in self.sources)
    if self.resources:
      allsources += list(os.path.join(RESOURCES_BASE_DIR, res) for res in self.resources)
    if self.binary_resources:
      allsources += list(os.path.join(RESOURCES_BASE_DIR, res) for res in self.binary_resources)

    return ExportableJvmLibrary._create_template_data(self).extend(
      java_sources = self.java_sources,
      resources = self.resources,
      binary_resources = self.binary_resources,
      deploy_jar = self.deployjar,
      allsources = allsources,
    )

class ScalaTests(JvmTarget):
  """Defines a target that tests a scala library."""

  @classmethod
  def _aggregate(cls, name, buildflags, scala_tests):
    all_deps = OrderedSet()
    all_excludes = OrderedSet()
    all_sources = []

    for scala_test in scala_tests:
      if scala_test.resolved_dependencies:
        all_deps.update(dep for dep in scala_test.jar_dependencies if dep.rev is not None)
      if scala_test.excludes:
        all_excludes.update(scala_test.excludes)
      if scala_test.sources:
        all_sources.extend(scala_test.sources)

    return ScalaTests(name,
                      all_sources,
                      dependencies = all_deps,
                      buildflags = buildflags,
                      is_meta = True)

  def __init__(self,
               name,
               sources,
               dependencies = None,
               excludes = None,
               buildflags = None,
               is_meta = False):

    """name: The name of this module target, addressable via pants via the portion of the spec
        following the colon
    sources: A list of paths containing the scala source files this modules tests are compiled from
    provides: An optional Dependency object indicating the The ivy artifact to export
    dependencies: An optional list of Dependency objects specifying the binary (jar) dependencies of
        this module.
    excludes: An optional list of dependency exclude patterns to filter all of this module's
        transitive dependencies against.
    buildflags: A list of additional command line arguments to pass to the underlying build system
        for this target"""

    def get_all_deps():
      all_deps = OrderedSet()
      all_deps.update(Pants('src/scala/com/twitter/common/testing:explicit-specs-runner').resolve())
      all_deps.update(Pants('3rdparty:scala-library').resolve())
      if dependencies:
        all_deps.update(dependencies)
      return all_deps

    JvmTarget.__init__(self,
                        'tests/scala',
                        name,
                        sources,
                        get_all_deps(),
                        excludes,
                        buildflags,
                        is_meta)

  def _create_template_data(self):
    jar_dependency, id, exported = self._get_artifact_info()

    if self.excludes:
      exclude_template_datas = [exclude._create_template_data() for exclude in self.excludes]
    else:
      exclude_template_datas = None

    return TemplateData(
      id = id,
      name = self.name,
      template_base = self.target_base,
      exported = exported,
      org = jar_dependency.org,
      module = jar_dependency.name,
      version = jar_dependency.rev,
      sources = self.sources,
      dependencies = [dep._create_template_data() for dep in self.jar_dependencies],
      excludes = exclude_template_datas,
      buildflags = self.buildflags,
    )

class PythonTarget(TargetWithSources):
  def __init__(self, target_base, name, sources, dependencies = None, is_meta = False):
    TargetWithSources.__init__(self, target_base, name, is_meta)

    self.sources = self._resolve_paths(target_base, sources)
    self.dependencies = dependencies if dependencies else OrderedSet()

  def _create_template_data(self):
    return TemplateData(
      name = self.name,
      template_base = self.target_base,
      sources = self.sources,
      dependencies = self.dependencies
    )

class PythonTests(PythonTarget):
  def __init__(self, name, sources, dependencies = None, is_meta = False):
    PythonTarget.__init__(self, 'tests/python', name, sources, dependencies, is_meta)
