try:
  import cPickle as pickle
except ImportError:
  import pickle

from contextlib import contextmanager
import hashlib
import os
import shutil

from twitter.common.collections import OrderedDict, OrderedSet

from twitter.pants.base.artifact_cache import ArtifactCache
from twitter.pants.base.build_invalidator import BuildInvalidator, CacheKeyGenerator, NO_SOURCES, TARGET_SOURCES
from twitter.pants.targets import JarDependency


class TaskError(Exception):
  """Raised to indicate a task has failed."""

class TargetError(TaskError):
  """Raised to indicate a task has failed for a subset of targets"""
  def __init__(self, targets, *args, **kwargs):
    TaskError.__init__(self, *args, **kwargs)
    self.targets = targets

class Task(object):
  @classmethod
  def setup_parser(cls, option_group, args, mkflag):
    """
      Subclasses can add flags to the pants command line using the given option group.  Flag names
      should be created with mkflag([name]) to ensure flags are properly namespaced amongst other
      tasks.
    """

  EXTRA_DATA = 'extra.data'

  def __init__(self, context):
    self.context = context
    self._cache_key_generator = CacheKeyGenerator()
    # TODO: Shared, remote build cache.
    self._artifact_cache = ArtifactCache(os.path.join(context.config.get('tasks', 'artifact_cache')))
    self._build_invalidator_dir = \
      os.path.join(context.config.get('tasks', 'build_invalidator'), self.__class__.__name__)


  def execute(self, targets):
    """
      Executes this task against the given targets which may be a subset of the current context
      targets.
    """

  def invalidate_for(self):
    """
      Subclasses can override and return an object that should be checked for changes when using
      changed to manage target invalidation.  If the pickled form of returned object changes
      between runs all targets will be invalidated.
    """

  def invalidate_for_files(self):
    """
      Subclasses can override and return a list of full paths to extra, non-source files that should
      be checked for changes when using changed to manage target invalidation. This is useful for tracking
      changes to pre-built build tools, e.g., the thrift compiler.
    """

  class CacheManager(object):
    """
      Manages cache checks, updates and invalidation keeping track of basic change and invalidation
      statistics.
    """
    def __init__(self, cache_key_generator, build_invalidator_dir, targets, extra_data, only_externaldeps):
      self._cache_key_generator = cache_key_generator
      self._invalidator = BuildInvalidator(build_invalidator_dir)
      self._targets = set(targets)
      self._extra_data = pickle.dumps(extra_data)  # extra_data may be None.
      self._sources = NO_SOURCES if only_externaldeps else TARGET_SOURCES
      self.changed = OrderedDict()  # Map from target to corresponding cache key.

      # Counts, purely for display purposes.
      self.changed_files = 0
      self.invalidated_files = 0
      self.invalidated_targets = 0
      self.foreign_invalidated_targets = 0

    def check(self, target):
      """Checks if a target has changed and invalidates it if so."""
      cache_key = self._key_for(target)
      if cache_key and self._invalidator.needs_update(cache_key):
        self._invalidate(target, cache_key)

    def update(self, cache_key):
      """Mark a changed or invalidated target as successfully processed."""
      self._invalidator.update(cache_key)

    def invalidate(self, target):
      """Forcefully mark a target as changed."""
      self._invalidate(target, self._key_for(target), indirect=True)

    def _key_for(self, target):
      def fingerprint_extra(sha):
        sha.update(self._extra_data)
        self._fingerprint_jardeps(target, sha)

      return self._cache_key_generator.key_for_target(
        target,
        sources=self._sources,
        fingerprint_extra=fingerprint_extra
      )

    _JAR_HASH_KEYS = (
      'org',
      'name',
      'rev',
      'force',
      'excludes',
      'transitive',
      'ext',
      'url',
      '_configurations'
    )

    def _fingerprint_jardeps(self, target, sha):
      internaltargets = OrderedSet()
      alltargets = OrderedSet()
      def fingerprint_external(target):
        internaltargets.add(target)
        if hasattr(target, 'dependencies'):
          alltargets.update(target.dependencies)
      target.walk(fingerprint_external)

      for external_target in alltargets - internaltargets:
        # TODO(John Sirois): Hashing on external targets should have a formal api - we happen to
        # know jars are special and python requirements __str__ works for this purpose.
        if isinstance(external_target, JarDependency):
          jarid = ''
          for key in Task.CacheManager._JAR_HASH_KEYS:
            jarid += str(getattr(external_target, key))
          sha.update(jarid)
        else:
          sha.update(str(external_target))

    def _invalidate(self, target, cache_key, indirect=False):
      if target in self._targets:
        self.changed[target] = cache_key
        if indirect:
          self.invalidated_files += cache_key.num_sources
          self.invalidated_targets += 1
        else:
          self.changed_files += cache_key.num_sources
      else:
        # invalidate a target to be processed in a subsequent round - this handles goal groups
        self._invalidator.invalidate(cache_key)
        self.foreign_invalidated_targets += 1

  @contextmanager
  def changed(self, targets, only_buildfiles=False, invalidate_dependants=False, invalidate_globally=False,
              build_artifacts=None, artifact_root=None):
    """
      Yields an iterable over the targets that have changed since the last check to a with block.
      If no exceptions are thrown by work in the block, the cache is updated for the targets,
      otherwise if a TargetError is thrown by the work in the block all targets except those in the
      TargetError are cached.

      :targets The targets to check for changes.
      :only_buildfiles If True, then just the target's BUILD files are checked for changes.
      :invalidate_dependants If True then any targets depending on changed targets are invalidated
      :invalidate_globally If True then if any target has changed, all targets are invalidated.
      :build_artifacts If not None, a list of paths to which build artifacts will be written by the caller. These
        artifacts will subsequently be cached. Tasks opt-in to using the artifact cache by providing this
        argument, although actual cache use is governed by the --{write-to, read-from}-artifact-cache flags.
      :artifact_root If not None, the cached artifact paths will be remembered relative to this dir.
      :returns: the subset of targets that have changed
    """
    # invalidate_for() may return an iterable that isn't a set, so we ensure a set here.
    extra_data = self.invalidate_for()
    if extra_data is not None:
      extra_data = set(extra_data)

    extra_files = self.invalidate_for_files()
    if extra_files is not None:
      extra_files = set(extra_files)
      if extra_data is None:
        extra_data = set()
      for f in extra_files:
        sha = hashlib.sha1()
        with open(f, "rb") as fd:
          sha.update(fd.read())
        extra_data = extra_data.add(sha.hexdigest())

    cache_manager = Task.CacheManager(self._cache_key_generator, self._build_invalidator_dir,
      targets, extra_data, only_buildfiles)

    for target in targets:
      cache_manager.check(target)

    if len(cache_manager.changed) > 0:
      if invalidate_globally:
        for target in targets:
          cache_manager.invalidate(target)
      elif invalidate_dependants:
        for target in (self.context.dependants(lambda t: t in cache_manager.changed.keys())).keys():
          cache_manager.invalidate(target)

    if invalidate_dependants or invalidate_globally:
      if cache_manager.foreign_invalidated_targets:
        self.context.log.info('Invalidated %d dependant targets '
                              'for the next round' % cache_manager.foreign_invalidated_targets)

      if cache_manager.changed_files:
        msg = 'Operating on %d files in %d changed targets' % (
          cache_manager.changed_files,
          len(cache_manager.changed)
        )
        if cache_manager.invalidated_files:
          if invalidate_globally:
            invalidation_msg = 'globally invalidated'
          else:
            invalidation_msg = 'invalidated dependant'
          msg += ' and %d files in %d %s targets' % (
            cache_manager.invalidated_files,
            cache_manager.invalidated_targets,
            invalidation_msg
          )
        self.context.log.info(msg)
    elif cache_manager.changed_files:
      self.context.log.info('Operating on %d files in %d changed targets' % (
        cache_manager.changed_files,
        len(cache_manager.changed)
      ))

    try:
      if len(cache_manager.changed) == 0:  # No changes.
        yield []
      else:
        artifact_key = self._cache_key_generator.key_for_targets(targets)
        if self.context.options.read_from_artifact_cache and build_artifacts and \
            self._artifact_cache.has(artifact_key):
          self.context.log.info('Using cached artifacts for %s' % str(targets))
          self._artifact_cache.use_cached_files(artifact_key,
            lambda src, reldest: shutil.copy(src, os.path.join(artifact_root, reldest)))
          yield []  # Restoring from cache, so no need for caller to do anything.
        else:
          self.context.log.info('Building targets %s' % str(cache_manager.changed.keys()))
          yield cache_manager.changed.keys()  # Caller must rebuild.

        for cache_key in cache_manager.changed.values():
          cache_manager.update(cache_key)

        if self.context.options.write_to_artifact_cache and build_artifacts and \
            not self._artifact_cache.has(artifact_key):
          # if the caller provided paths to artifacts but we didn't previously have them in the cache,
          # we assume that they are now created, and store them in the artifact cache.
          self.context.log.info('Caching artifacts for %s' % str(targets))
          self._artifact_cache.insert(artifact_key, build_artifacts, artifact_root)

    except TargetError as e:
      for target, cache_key in cache_manager.changed.items():
        if target not in e.targets:
          cache_manager.update(cache_key)

__all__ = (
  'TaskError',
  'TargetError',
  'Task'
)
