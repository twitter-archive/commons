
import os
from collections import defaultdict

from twitter.common.collections import OrderedSet

from twitter.pants.targets import JvmTarget, JarLibrary, InternalTarget, JarDependency
from twitter.pants.tasks import TaskError, Task
from twitter.pants import get_buildroot


class JvmDependencyAnalyzer(object):
  def __init__(self, context, check_missing_deps, check_missing_direct_deps, check_unnecessary_deps):
    self._context = context
    self._context.products.require('classes')
    self._context.products.require('ivy_jar_products')

    self._check_missing_deps = check_missing_deps
    self._check_missing_direct_deps = check_missing_direct_deps
    self._check_unnecessary_deps = check_unnecessary_deps

    # Memoized map from absolute path of source, class or jar file to an OrderedSet of targets.
    #
    # The value is usually a singleton, because a source or class file belongs to a single target.
    # However a single jar may be provided (transitively or intransitively) by multiple JarLibrary targets.
    # But if there is a JarLibrary target that depends on a jar directly, then that "canonical" target
    # will be the first one in the list of targets.
    #
    # Don't access directly. Call self.get_targets_by_file() instead.
    self._targets_by_file = None

    # Memoized map from target to all its transitive dep targets.
    # Don't access directly. Call self.get_transitive_deps_by_target() instead.
    self._transitive_deps_by_target = None

  def get_targets_by_file(self):
    """Compute the targets_by_file mapping for all targets in-play for this pants run.

    Memoizes for efficiency. Should only be called after codegen, so that all synthetic targets
    and injected deps are taken into account.
    """
    if self._targets_by_file is None:
      return self._compute_targets_by_file()  # Will memoize if allowed.
    else:
      return self._targets_by_file

  def _compute_targets_by_file(self):
    targets_by_file = defaultdict(OrderedSet)
    jarlibs_by_id = defaultdict(set)  # Multiple JarLibrary targets can provide the same (org, name).
    # Compute src -> target.
    buildroot = get_buildroot()
    # Look at all targets in-play for this pants run. Does not include synthetic targets,
    for target in self._context.targets():
      if isinstance(target, JvmTarget):
        for src in target.sources:
          targets_by_file[os.path.join(buildroot, target.target_base, src)].add(target)
      elif isinstance(target, JarLibrary):
        for jardep in target.dependencies:
          if isinstance(jardep, JarDependency):
            jarlibs_by_id[(jardep.org, jardep.name)].add(target)

    # Compute class -> target for classes in previous compile groups.
    genmap = self._context.products.get('classes')
    for tgt, products in genmap.itermappings():
      # tgt could also be a string, as we (ab)use the same genmap for the relsrc->classes mapping.
      if isinstance(tgt, JvmTarget):
        for basedir, classes in products.items():
          for cls in classes:
            targets_by_file[os.path.join(basedir, cls)].add(tgt)

    # Compute jar -> target.
    with Task.symlink_map_lock:
      all_symlinks_map = self._context.products.get_data('symlink_map').copy()
      # We make a copy, so it's safe to use outside the lock.

    ivy_products = self._context.products.get_data('ivy_jar_products')
    if ivy_products:
      for ivyinfos in ivy_products.values():
        for ivyinfo in ivyinfos:
          for ref in ivyinfo.modules_by_ref:
            target_key = (ref.org, ref.name)
            if target_key in jarlibs_by_id:
              jarlib_targets = jarlibs_by_id[target_key]
              for jar in ivyinfo.modules_by_ref[ref].artifacts:
                symlinks = all_symlinks_map.get(os.path.realpath(jar.path), [])
                for symlink in symlinks:
                  for jarlib_target in jarlib_targets:
                    targets_by_file[symlink].add(jarlib_target)
              # Map all indirect, transitive deps of the jar to this target as well, since we allow deps on them.
              for dep in ivyinfo.deps_by_caller.get(ref, []):
                depmodule = ivyinfo.modules_by_ref.get(dep, None)
                if depmodule:
                  for depjar in depmodule.artifacts:
                    symlinks = all_symlinks_map.get(depjar.path, [])
                    for symlink in symlinks:
                      for jarlib_target in jarlib_targets:
                        targets_by_file[symlink].add(jarlib_target)
      # Only memoize once ivy_jar_products are available.
      self._targets_by_file = targets_by_file
    return targets_by_file

  def get_transitive_deps_by_target(self):
    """Compute the transitive_deps_by_target mapping from all targets in-play for this pants run.

    Memoizes for efficiency. Should only be called after codegen, so that all synthetic targets
    and injected deps are taken into account.
    """
    if self._transitive_deps_by_target is None:
      return self._compute_transitive_deps_by_target()
    else:
      return self._transitive_deps_by_target

  def _compute_transitive_deps_by_target(self):
    # Sort from least to most dependent.
    sorted_targets = reversed(InternalTarget.sort_targets(self._context.targets()))
    transitive_deps_by_target = defaultdict(set)
    # Iterate in dep order, to accumulate the transitive deps for each target.
    for target in sorted_targets:
      transitive_deps = set()
      if hasattr(target, 'dependencies'):
        for dep in target.dependencies:
          transitive_deps.update(transitive_deps_by_target.get(dep, []))
          transitive_deps.add(dep)
        transitive_deps_by_target[target] = transitive_deps
    self._transitive_deps_by_target = transitive_deps_by_target  # Memoize.
    return transitive_deps_by_target

  def check(self, srcs, actual_deps):
    """Check for missing deps.

    See docstring for _compute_missing_deps for details.
    """
    if self._check_missing_deps or self._check_missing_direct_deps or self._check_unnecessary_deps:
      missing_file_deps, missing_tgt_deps, missing_direct_tgt_deps = \
        self._compute_missing_deps(srcs, actual_deps)

      buildroot = get_buildroot()
      def shorten(path):  # Make the output easier to read.
        for prefix in [buildroot, self._context.ivy_home]:
          if path.startswith(prefix):
            return os.path.relpath(path, prefix)
        return path

      if self._check_missing_deps and (missing_file_deps or missing_tgt_deps):
        for (tgt_pair, evidence) in missing_tgt_deps:
          evidence_str = '\n'.join(['    %s uses %s' % (shorten(e[0]), shorten(e[1])) for e in evidence])
          self._context.log.error('Missing BUILD dependency %s -> %s because:\n%s' %
                                  (tgt_pair[0].address.reference(), tgt_pair[1].address.reference(), evidence_str))
        for (src_tgt, dep) in missing_file_deps:
          self._context.log.error('Missing BUILD dependency %s -> %s' % (src_tgt.address.reference(), shorten(dep)))
        if self._check_missing_deps == 'fatal':
          raise TaskError('Missing deps.')

      if self._check_missing_direct_deps:
        for (tgt_pair, evidence) in missing_direct_tgt_deps:
          evidence_str = '\n'.join(['    %s uses %s' % (shorten(e[0]), shorten(e[1])) for e in evidence])
          self._context.log.warn('Missing direct BUILD dependency %s -> %s because:\n%s' %
                                  (tgt_pair[0].address, tgt_pair[1].address, evidence_str))
        if self._check_missing_direct_deps == 'fatal':
          raise TaskError('Missing direct deps.')

      if self._check_unnecessary_deps:
        raise TaskError('Unnecessary dep warnings not implemented yet.')

  def _compute_missing_deps(self, srcs, actual_deps):
    """Computes deps that are used by the compiler but not specified in a BUILD file.

    These deps are bugs waiting to happen: the code may happen to compile because the dep was brought
    in some other way (e.g., by some other root target), but that is obviously fragile.

    Note that in practice we're OK with reliance on indirect deps that are only brought in transitively.
    E.g., in Scala type inference can bring in such a dep subtly. Fortunately these cases aren't as fragile
    as a completely missing dependency. It's still a good idea to have explicit direct deps where relevant,
    so we optionally warn about indirect deps, to make them easy to find and reason about.

    - actual_deps: a map src -> list of actual deps (source, class or jar file) as noted by the compiler.

    Returns a triple (missing_file_deps, missing_tgt_deps, missing_direct_tgt_deps) where:

    - missing_file_deps: a list of pairs (src_tgt, dep_file) where src_tgt requires dep_file, and we're
      unable to map to a target (because its target isn't in the total set of targets in play, and we
      don't want to parse every BUILD file in the workspace just to find it).

    - missing_tgt_deps: a list of pairs (src_tgt, dep_tgt) where src_tgt is missing a necessary
                        transitive dependency on dep_tgt.

    - missing_direct_tgt_deps: a list of pairs (src_tgt, dep_tgt) where src_tgt is missing a direct
                               dependency on dep_tgt but has a transitive dep on it.

    All paths in the input and output are absolute.
    """
    def must_be_explicit_dep(dep):
      # We don't require explicit deps on the java runtime, so we shouldn't consider that
      # a missing dep.
      return not dep.startswith(self._context.java_home)

    targets_by_file = self.get_targets_by_file()
    transitive_deps_by_target = self.get_transitive_deps_by_target()

    # Find deps that are actual but not specified.
    missing_file_deps = []  # (src, src).
    missing_tgt_deps_map = defaultdict(list)  # (tgt, tgt) -> a list of (src, src) as evidence.
    missing_direct_tgt_deps_map = defaultdict(list)  # The same, but for direct deps.

    buildroot = get_buildroot()
    abs_srcs = [os.path.join(buildroot, src) for src in srcs]
    for src in abs_srcs:
      src_tgt = next(iter(targets_by_file.get(src)))
      if src_tgt is not None:
        for actual_dep in filter(must_be_explicit_dep, actual_deps.get(src, [])):
          actual_dep_tgts = targets_by_file.get(actual_dep)
          # actual_dep_tgts is usually a singleton. If it's not, we only need one of these
          # to be in our declared deps to be OK.
          if actual_dep_tgts is None:
            missing_file_deps.append((src_tgt, actual_dep))
          elif src_tgt not in actual_dep_tgts:  # Obviously intra-target deps are fine.
            canonical_actual_dep_tgt = next(iter(actual_dep_tgts))
            if actual_dep_tgts.isdisjoint(transitive_deps_by_target.get(src_tgt, [])):
              missing_tgt_deps_map[(src_tgt, canonical_actual_dep_tgt)].append((src, actual_dep))
            elif canonical_actual_dep_tgt not in src_tgt.dependencies:
              # The canonical dep is the only one a direct dependency makes sense on.
              missing_direct_tgt_deps_map[(src_tgt, canonical_actual_dep_tgt)].append((src, actual_dep))
      else:
        raise TaskError('Requested dep info for unknown source file: %s' % src)

    return missing_file_deps, missing_tgt_deps_map.items(), missing_direct_tgt_deps_map.items()
