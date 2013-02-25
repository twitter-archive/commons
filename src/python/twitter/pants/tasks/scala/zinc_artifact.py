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

__author__ = 'Benjy Weinberger'

import itertools
import os
import shutil

from collections import defaultdict, namedtuple

from twitter.common.contextutil import temporary_dir
from twitter.common.dirutil import safe_mkdir

from twitter.pants.base.target import Target
from twitter.pants.targets import resolve_target_sources
from twitter.pants.targets.scala_library import ScalaLibrary
from twitter.pants.targets.scala_tests import ScalaTests
from twitter.pants.tasks import TaskError
from twitter.pants.tasks.scala.zinc_artifact_state import ZincArtifactState, ZincArtifactStateDiff


class ZincArtifactFactory(object):
  """Creates objects representing zinc artifacts."""
  def __init__(self, workdir, context, zinc_utils):
    self._workdir = workdir
    self.context = context
    self.zinc_utils = zinc_utils

    self._classes_dirs_base = os.path.join(self._workdir, 'classes')
    self._analysis_files_base = os.path.join(self._workdir, 'analysis')
    safe_mkdir(self._classes_dirs_base)
    safe_mkdir(self._analysis_files_base)

  def artifact_for_target(self, target):
    """The artifact representing the specified target."""
    targets = [target]
    sources_by_target = {target: ZincArtifactFactory._calculate_sources(target)}
    factory = self
    return _ZincArtifact(factory, targets, sources_by_target, *self._artifact_args([target]))

  def merged_artifact(self, artifacts):
    """The artifact merged from those of the specified artifacts."""
    targets = list(itertools.chain.from_iterable([a.targets for a in artifacts]))
    sources_by_target = dict(itertools.chain.from_iterable([a.sources_by_target.items() for a in artifacts]))
    factory = self
    return _MergedZincArtifact(artifacts, factory, targets, sources_by_target, *self._artifact_args(targets))

  # Useful for when we only need access to the analysis file, and don't have an artifact object.
  def analysis_file_for_targets(self, targets):
    return self._artifact_args(targets)[2]

  # There are two versions of the zinc analysis file: The one zinc creates on compilation, which
  # contains full paths and is therefore not portable, and the portable version, that we create by rebasing
  # the full path prefixes to placeholders. We refer to this as "relativizing" the analysis file.
  # The inverse, replacing placeholders with full path prefixes so we can use the file again when compiling,
  # is referred to as "localizing" the analysis file.
  #
  # This is necessary only when using the artifact cache: We must relativize before uploading to the cache,
  # and localize after pulling from the cache.
  @staticmethod
  def portable(analysis_file):
    """Returns the path to the portable version of the zinc analysis file."""
    return analysis_file + '.portable'

  def _artifact_args(self, targets):
    """Returns the artifact paths for the given target set."""
    artifact_id = Target.maybe_readable_identify(targets)
    # Each compilation must output to its own directory, so zinc can then associate those with the appropriate
    # analysis files of previous compilations.
    classes_dir = os.path.join(self._classes_dirs_base, artifact_id)
    analysis_file = os.path.join(self._analysis_files_base, artifact_id) + '.analysis'
    return artifact_id, classes_dir, analysis_file

  @staticmethod
  def _calculate_sources(target):
    """Find a target's source files."""
    sources = []
    srcs = [os.path.join(target.target_base, src) for src in target.sources if src.endswith('.scala')]
    sources.extend(srcs)
    if (isinstance(target, ScalaLibrary) or isinstance(target, ScalaTests)) and target.java_sources:
      sources.extend(resolve_target_sources(target.java_sources, '.java'))
    return sources


class _ZincArtifact(object):
  """Locations of the files in a zinc build artifact.

  An artifact consists of:
    A) A classes directory
    B) A zinc analysis file.

  Represents the result of building some set of targets.

  Don't create instances of this directly. Use ZincArtifactFactory instead.
  """
  def __init__(self, factory, targets, sources_by_target,
               artifact_id, classes_dir, analysis_file):
    self.factory = factory
    self.targets = targets
    self.sources_by_target = sources_by_target
    self.sources = list(itertools.chain.from_iterable(sources_by_target.values()))
    self.artifact_id = artifact_id
    self.classes_dir = classes_dir
    self.analysis_file = analysis_file
    self.portable_analysis_file = ZincArtifactFactory.portable(analysis_file)
    self.relations_file = analysis_file + '.relations'  # The human-readable zinc relations file.

  def current_state(self):
    """Returns the current state of this artifact."""
    return ZincArtifactState(self)

  def __eq__(self, other):
    return self.artifact_id == other.artifact_id

  def __ne__(self, other):
    return self.artifact_id != other.artifact_id


class _MergedZincArtifact(_ZincArtifact):
  """An artifact merged from some underlying artifacts.

  A merged artifact consists of:
    A) A classes directory containing all the classes from all the underlying artifacts' classes directories.
    B) A zinc analysis file containing all the information from all the underlying artifact's analysis files.
  """
  def __init__(self, underlying_artifacts, factory , targets, sources_by_target,
               artifact_id, classes_dir, analysis_file):
    _ZincArtifact.__init__(self, factory, targets, sources_by_target, artifact_id, classes_dir, analysis_file)
    self.underlying_artifacts = underlying_artifacts

  def merge(self):
    """Actually combines the underlying artifacts into a single merged one.

    Creates a single merged analysis file and a single merged classes dir.
    """
    # Note that if the merged analysis file already exists we don't re-merge it.
    # Ditto re the merged classes dir. In some unlikely corner cases they may
    # be less up to date than the artifact we could create by re-merging, but this
    # heuristic is worth it so that in the common case we don't spend a lot of time
    # copying files around.

    # Must merge analysis before computing current state.
    if not os.path.exists(self.analysis_file):
      self._merge_analysis()

    current_state = self.current_state()

    if not os.path.exists(self.classes_dir):
      self._merge_classes_dir(current_state)
    return current_state

  def _merge_analysis(self):
    """Merge the analysis files from the underlying artifacts into a single file."""
    if len(self.underlying_artifacts) <= 1:
      return
    with temporary_dir(cleanup=False) as tmpdir:
      artifact_analysis_files = []
      for artifact in self.underlying_artifacts:
        # Rebase a copy of the per-target analysis files to reflect the merged classes dir.
        if os.path.exists(artifact.classes_dir) and os.path.exists(artifact.analysis_file):
          analysis_file_tmp = os.path.join(tmpdir, artifact.artifact_id)
          shutil.copyfile(artifact.analysis_file, analysis_file_tmp)
          artifact_analysis_files.append(analysis_file_tmp)
          if self.factory.zinc_utils.run_zinc_rebase(analysis_file_tmp, [(artifact.classes_dir, self.classes_dir)]):
            self.factory.context.log.warn(
              'Zinc failed to rebase analysis file %s. Target may require a full rebuild.' % analysis_file_tmp)

      if self.factory.zinc_utils.run_zinc_merge(artifact_analysis_files, self.analysis_file):
        self.factory.context.log.warn(
          'zinc failed to merge analysis files %s to %s. Target may require a full rebuild.' % \
                               (':'.join(artifact_analysis_files), self.analysis_file))

  def _merge_classes_dir(self, state):
    """Merge the classes dirs from the underlying artifacts into a single dir.

    May symlink instead of copying, when it's OK to do so.

    Postcondition: symlinks are of leaf packages only.
    """
    if len(self.underlying_artifacts) <= 1:
      return
    for artifact in self.underlying_artifacts:
      classnames_by_package = defaultdict(list)
      for cls in state.classes_by_target.get(artifact.targets[0], []):
        classnames_by_package[os.path.dirname(cls)].append(os.path.basename(cls))

      for package, classnames in classnames_by_package.items():
        artifact_package_dir = os.path.join(artifact.classes_dir, package)
        merged_package_dir = os.path.join(self.classes_dir, package)

        ancestor_symlink = _MergedZincArtifact.find_ancestor_package_symlink(self.classes_dir, merged_package_dir)
        if not os.path.exists(merged_package_dir) and not ancestor_symlink:
          # A heuristic to prevent tons of file copying: If we're the only classes
          # in this package, we can just symlink.
          safe_mkdir(os.path.dirname(merged_package_dir))
          os.symlink(artifact_package_dir, merged_package_dir)
        else:
          # Another target already "owns" this package, so we can't use the symlink heuristic.
          # Instead, we fall back to copying. Note that the other target could have been from
          # a prior invocation of execute(), so it may not be in self.underlying_artifacts.
          if ancestor_symlink:
            # Must undo a previous symlink heuristic in this case.
            package_dir_for_some_other_target = os.readlink(ancestor_symlink)
            os.unlink(ancestor_symlink)
            shutil.copytree(package_dir_for_some_other_target, ancestor_symlink)
          safe_mkdir(merged_package_dir)
          for classname in classnames:
            src = os.path.join(artifact_package_dir, classname)
            dst = os.path.join(merged_package_dir, classname)
            # dst may already exist if we have overlapping targets. It's not a good idea
            # to have those, but until we enforce it, we must allow it here.
            if os.path.exists(src) and not os.path.exists(dst):
              os.link(src, dst)

  def split(self, old_state=None, portable=False):
    """Actually split the merged artifact into per-target artifacts."""
    current_state = self.current_state()
    diff = ZincArtifactStateDiff(old_state, current_state) if old_state else None
    if not diff or diff.analysis_changed:
      self._split_analysis('analysis_file')
      if portable:
        self._split_analysis('portable_analysis_file')
    self._split_classes_dir(current_state, diff)
    return current_state

  def _split_analysis(self, analysis_file_attr):
    """Split the merged analysis into one file per underlying artifact.

    analysis_file_attr: one of 'analysis_file' or 'portable_analysis_file'.
    """
    if len(self.underlying_artifacts) <= 1:
      return
    # Specifies that the list of sources defines a split to the classes dir and analysis file.
    SplitInfo = namedtuple('SplitInfo', ['sources', 'dst_classes_dir', 'dst_analysis_file'])

    def _analysis(artifact):
      return getattr(artifact, analysis_file_attr)

    if len(self.underlying_artifacts) <= 1:
      return

    analysis_to_split = _analysis(self)
    if not os.path.exists(analysis_to_split):
      return

    splits = []
    for artifact in self.underlying_artifacts:
      splits.append(SplitInfo(artifact.sources, artifact.classes_dir, _analysis(artifact)))

    split_args = [(x.sources, x.dst_analysis_file) for x in splits]
    if self.factory.zinc_utils.run_zinc_split(analysis_to_split, split_args):
      raise TaskError, 'zinc failed to split analysis files %s from %s' % \
                       (':'.join([x.dst_analysis_file for x in splits]), analysis_to_split)
    for split in splits:
      if os.path.exists(split.dst_analysis_file):
        if self.factory.zinc_utils.run_zinc_rebase(split.dst_analysis_file,
                                                   [(self.classes_dir, split.dst_classes_dir)]):
          raise TaskError, 'Zinc failed to rebase analysis file %s' % split.dst_analysis_file

  def _split_classes_dir(self, state, diff):
    """Split the merged classes dir into one dir per underlying artifact."""
    if len(self.underlying_artifacts) <= 1:
      return

    def map_classes_by_package(classes):
      # E.g., com/foo/bar/Bar.scala, com/foo/bar/Baz.scala becomes com/foo/bar -> [Bar.scala, Baz.scala].
      ret = defaultdict(list)
      for cls in classes:
        ret[os.path.dirname(cls)].append(os.path.basename(cls))
      return ret

    if diff:
      new_or_changed_classnames_by_package = map_classes_by_package(diff.new_or_changed_classes)
      deleted_classnames_by_package = map_classes_by_package(diff.deleted_classes)
    else:
      new_or_changed_classnames_by_package = None
      deleted_classnames_by_package = None

    for artifact in self.underlying_artifacts:
      classnames_by_package = map_classes_by_package(state.classes_by_target.get(artifact.targets[0], []))

      # We iterate from longest to shortest package name, so that we see child packages
      # before parent packages. This guarantees that we only symlink leaf packages.
      package_classnames_pairs = sorted(classnames_by_package.items(), key=lambda kv: len(kv[0]), reverse=True)

      for package, classnames in package_classnames_pairs:
        artifact_package_dir = os.path.join(artifact.classes_dir, package)
        merged_package_dir = os.path.join(self.classes_dir, package)

        if os.path.islink(merged_package_dir):
          linked = os.readlink(merged_package_dir)
          if linked != artifact_package_dir:
            # Two targets have classes in this package.
            # First get rid of this now-invalid symlink, replacing it with a copy.
            os.unlink(merged_package_dir)
            shutil.copytree(linked, merged_package_dir)
            # Now remove our classes from the other target's dir.
            our_classnames = set(classnames)
            for f in os.listdir(linked):
              if f in our_classnames:
                os.unlink(os.path.join(linked, f))
            # Make sure we copy our files, and don't attempt the symlink heuristic below.
            safe_mkdir(artifact_package_dir)
          else:
            continue
        # If we get here then merged_package_dir is not a symlink.

        if not os.path.exists(artifact_package_dir):
          # Apply the symlink heuristic on this new package.
          shutil.move(merged_package_dir, artifact_package_dir)
          os.symlink(artifact_package_dir, merged_package_dir)
        else:
          new_or_changed_classnames = set(new_or_changed_classnames_by_package.get(package, [])) if diff else None
          for classname in classnames:
            if not diff or classname in new_or_changed_classnames:
              src = os.path.join(merged_package_dir, classname)
              dst = os.path.join(artifact_package_dir, classname)
              shutil.copyfile(src, dst)
          if diff:
            for classname in deleted_classnames_by_package.get(package, []):
              path = os.path.join(artifact_package_dir, classname)
              if os.path.exists(path):
                os.unlink(path)

  @staticmethod
  def find_ancestor_package_symlink(base, dir):
    """Returns the first ancestor package of dir (including itself) under base that is a symlink."""
    while len(dir) > len(base):
      if os.path.islink(dir):
        return dir
      dir = os.path.dirname(dir)
    return None

