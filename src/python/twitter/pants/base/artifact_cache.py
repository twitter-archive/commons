# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

import httplib
import os
import shutil
import urlparse

from twitter.common.contextutil import open_zip, temporary_file
from twitter.common.dirutil import safe_mkdir, safe_rmtree
from zipfile import ZIP_STORED, ZIP_DEFLATED


# Note throughout the distinction between the artifact_root (which is where the artifacts are originally built
# and where the cache restores them to) and the cache root path/URL (which is where the artifacts are cached).

def create_artifact_cache(context, artifact_root, spec):
  """
    Returns an artifact cache for the specified spec. If config is a string, it's interpreted
    as a path or URL prefix to a cache root. If it's a list of strings, it returns an appropriate
    combined cache.
  """
  if len(spec) == 0:
    raise Exception, 'Empty artifact cache spec'
  if isinstance(spec, basestring):
    if spec.startswith('/'):
      return FileBasedArtifactCache(context, artifact_root, spec)
    elif spec.startswith('http://'):
      return RESTfulArtifactCache(context, artifact_root, spec)
    else:
      raise Exception, 'Invalid artifact cache spec: %s' % spec
  elif isinstance(spec, (list, tuple)):
    caches = [ create_artifact_cache(context, artifact_root, x) for x in spec ]
    return CombinedArtifactCache(caches)


class ArtifactCache(object):
  """
    A map from cache key to a set of build artifacts.

    The cache key must uniquely identify the inputs (sources, compiler flags etc.) needed to
    build the artifacts. Cache keys are typically obtained from a CacheKeyGenerator.

    Subclasses implement the methods below to provide this functionality.
  """
  def __init__(self, context, artifact_root):
    """Create an ArtifactCache.

    All artifacts must be under artifact_root.
    """
    self.context = context
    self.artifact_root = artifact_root

  def insert(self, cache_key, build_artifacts):
    """Cache the output of a build.

    If there is an existing set of artifacts for this key they are deleted.

    TODO: Check that they're equal? If they aren't it's a grave bug, since the key is supposed
    to be a fingerprint of all possible inputs to the build.

    cache_key: A CacheKey object.
    build_artifacts: List of paths to generated artifacts. These must be under pants_workdir.
    """
    try:
      self.try_insert(cache_key, build_artifacts)
    except Exception as e:
      try:
        self.delete(cache_key)
      except Exception as e:
        print('IMPORTANT: failed to delete %s on error. Your artifact cache may be corrupted. '
              'Please delete manually.' % cache_key)
      raise e

  def try_insert(self, cache_key, build_artifacts):
    """Attempt to cache the output of a build, without error-handling.

    If there is an existing set of artifacts for this key they are deleted.

    cache_key: A CacheKey object.
    build_artifacts: List of paths to generated artifacts. These must be under pants_workdir.
    """
    pass

  def has(self, cache_key):
    pass

  def use_cached_files(self, cache_key):
    """Use the artifacts cached for the given key.

    Returns True if files were found and used, False otherwise.

    cache_key: A CacheKey object.
    """
    pass

  def delete(self, cache_key):
    """Delete the artifacts for the specified key.

    Deleting non-existent artifacts is a no-op.
    """
    pass


class FileBasedArtifactCache(ArtifactCache):
  """An artifact cache that stores the artifacts in local files."""
  def __init__(self, context, artifact_root, cache_root, copy_fn=None):
    """
    cache_root: The locally cached files are stored under this directory.
    copy_fn: An optional function with the signature copy_fn(absolute_src_path, relative_dst_path) that
        will copy cached files into the desired destination. If unspecified, a simple file copy is used.
    """
    ArtifactCache.__init__(self, context, artifact_root)
    self._cache_root = cache_root
    self._copy_fn = copy_fn if copy_fn else \
      lambda src, rel_dst: shutil.copy(src, os.path.join(self.artifact_root, rel_dst))
    safe_mkdir(self._cache_root)

  def try_insert(self, cache_key, build_artifacts):
    cache_dir = self._cache_dir_for_key(cache_key)
    safe_rmtree(cache_dir)
    for artifact in build_artifacts or ():
      rel_path = os.path.relpath(artifact, self.artifact_root)
      assert not rel_path.startswith('..'), \
        'Artifact %s is not under artifact root %s' % (artifact, self.artifact_root)
      artifact_dest = os.path.join(cache_dir, rel_path)
      dir_name = os.path.dirname(artifact_dest)
      safe_mkdir(dir_name)
      if os.path.isdir(artifact):
        shutil.copytree(artifact, artifact_dest)
      else:
        shutil.copy(artifact, artifact_dest)

  def has(self, cache_key):
    return os.path.isdir(self._cache_dir_for_key(cache_key))

  def use_cached_files(self, cache_key):
    cache_dir = self._cache_dir_for_key(cache_key)
    if not os.path.exists(cache_dir):
      return False
    for dir_name, _, filenames in os.walk(cache_dir):
      for filename in filenames:
        filename = os.path.join(dir_name, filename)
        relative_filename = os.path.relpath(filename, cache_dir)
        self._copy_fn(filename, relative_filename)
    return True

  def delete(self, cache_key):
    cache_dir = self._cache_dir_for_key(cache_key)
    safe_rmtree(cache_dir)

  def _cache_dir_for_key(self, cache_key):
    return os.path.join(self._cache_root, cache_key.hash)


class RESTfulArtifactCache(ArtifactCache):
  """An artifact cache that stores the artifacts on a RESTful service."""
  def __init__(self, context, artifact_root, url_base, compress=True):
    """
    url_base: The prefix for urls on some RESTful service. We must be able to PUT and GET to any
              path under this base.
    compress: Whether to compress the artifacts before storing them.
    """
    ArtifactCache.__init__(self, context, artifact_root)
    parsed_url = urlparse.urlparse(url_base)
    if parsed_url.scheme != 'http':
      raise Exception, 'RESTfulArtifactCache only supports HTTP'
    self._netloc = parsed_url.netloc
    self._path_prefix = parsed_url.path
    if self._path_prefix.endswith('/'):
      self._path_prefix = self._path_prefix[:-1]
    self._conn = self._connect()
    self.compression = ZIP_DEFLATED if compress else ZIP_STORED

  def try_insert(self, cache_key, build_artifacts):
    path = self._path_for_key(cache_key)
    with temporary_file() as zipfile:
      with open_zip(zipfile, 'w', compression=self.compression) as zipout:
        def write_file_to_zip(path):
          rel_path = os.path.relpath(path, self.artifact_root)
          zipout.write(path, rel_path)

        for artifact in build_artifacts or ():
          if os.path.isfile(artifact):
            write_file_to_zip(artifact)
          elif os.path.isdir(artifact):
            for dirname, _, files in os.walk(artifact):
              for file in files:
                write_file_to_zip(os.path.join(dirname, file))

      with open(zipfile.name, 'rb') as infile:
        if not self._request('PUT', path, body=infile):
          raise Exception, 'Failed to PUT to http://%s/%s. Error: 404' % (self._netloc, path)

  def has(self, cache_key):
    path = self._path_for_key(cache_key)
    response = self._request('HEAD', path)
    return response is not None

  def use_cached_files(self, cache_key):
    path = self._path_for_key(cache_key)
    response = self._request('GET', path)
    if response is None:
      return False
    read_size = 4 * 1024*1024 # 4 MB
    done = False
    with temporary_file() as outfile:
      while not done:
        data = response.read(read_size)
        outfile.write(data)
        if len(data) < read_size:
          done = True
        outfile.close()
      with open_zip(outfile.name, 'r') as zipfile:
        zipfile.extractall(self.artifact_root)
    return True

  def delete(self, cache_key):
    path = self._path_for_key(cache_key)
    self._request('DELETE', path)

  def _path_for_key(self, cache_key):
    return '%s/%s.zip' % (self._path_prefix, cache_key.hash)

  def _connect(self):
    return httplib.HTTPConnection(self._netloc)

  # Returns a response if we get a 200, None if we get a 404 and raises an exception otherwise.
  def _request(self, method, path, body=None):
    if self.context:
      self.context.log.info('Sending %s request to http://%s%s' % (method, self._netloc, path))
    self._conn = self._connect()
    self._conn.request(method, path, body=body)
    response = self._conn.getresponse()
    if response.status == 200:  # TODO: Can HEAD return 204? It would be correct, but I've not seen it happen.
      return response
    elif response.status == 404:
      return None
    else:
      raise Exception, 'Failed to %s to http://%s/%s. Error: %d %s' % \
                       (method, self._netloc, path, response.status, response.reason)


class CombinedArtifactCache(ArtifactCache):
  """An artifact cache that delegates to a list of other caches."""
  def __init__(self, artifact_caches):
    if len(artifact_caches) == 0:
      raise Exception, 'Must provide at least one underlying artifact cache'
    context = artifact_caches[0].context
    artifact_root = artifact_caches[0].artifact_root
    if any([x.context != context or x.artifact_root != artifact_root for x in artifact_caches]):
      raise Exception, 'Combined artifact caches must all have the same artifact root.'
    ArtifactCache.__init__(self, context, artifact_root)
    self._artifact_caches = artifact_caches

  def insert(self, cache_key, build_artifacts):
    for cache in self._artifact_caches:  # Insert into all.
      cache.insert(cache_key, build_artifacts)

  def has(self, cache_key):
    for cache in self._artifact_caches:  # Read from any.
      if cache.has(cache_key):
        return True
    return False

  def use_cached_files(self, cache_key):
    for cache in self._artifact_caches:  # Read from any.
      if cache.use_cached_files(cache_key):
        return True
    return False

  def delete(self, cache_key):
    for cache in self._artifact_caches:  # Delete from all.
      cache.delete(cache_key)
