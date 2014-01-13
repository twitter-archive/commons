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
from __future__ import absolute_import

import logging
import os
import sys
import tempfile
from zipimport import zipimporter

from .compatibility import to_bytes
from .common import chmod_plus_x, safe_mkdir, Chroot
from .interpreter import PythonInterpreter
from .marshaller import CodeMarshaller
from .pex_info import PexInfo
from .translator import dist_from_egg
from .util import DistributionHelper

from pkg_resources import (
    DefaultProvider,
    Distribution,
    EggMetadata,
    PathMetadata,
    Requirement,
    ZipProvider,
    get_provider,
)


BOOTSTRAP_ENVIRONMENT = b"""
import os
import sys

__entry_point__ = None
if '__file__' in locals() and __file__ is not None:
  __entry_point__ = os.path.dirname(__file__)
elif '__loader__' in locals():
  from zipimport import zipimporter
  from pkgutil import ImpLoader
  if hasattr(__loader__, 'archive'):
    __entry_point__ = __loader__.archive
  elif isinstance(__loader__, ImpLoader):
    __entry_point__ = os.path.dirname(__loader__.get_filename())

if __entry_point__ is None:
  sys.stderr.write('Could not launch python executable!\\n')
  sys.exit(2)

sys.path[0] = os.path.abspath(sys.path[0])
sys.path.insert(0, os.path.abspath(os.path.join(__entry_point__, '.bootstrap')))

from _twitter_common_python.pex_bootstrapper import bootstrap_pex
bootstrap_pex(__entry_point__)
"""

class PEXBuilder(object):
  class InvalidDependency(Exception): pass
  class InvalidExecutableSpecification(Exception): pass

  BOOTSTRAP_DIR = ".bootstrap"

  def __init__(self, path=None, interpreter=None, chroot=None, pex_info=None):
    self._chroot = chroot or Chroot(path or tempfile.mkdtemp())
    self._pex_info = pex_info or PexInfo.default()
    self._frozen = False
    self._interpreter = interpreter or PythonInterpreter.get()
    self._logger = logging.getLogger(__name__)

  def chroot(self):
    return self._chroot

  def clone(self, into=None):
    chroot_clone = self._chroot.clone(into=into)
    return PEXBuilder(chroot=chroot_clone, interpreter=self._interpreter,
                      pex_info=PexInfo(content=self._pex_info.dump()))

  def path(self):
    return self.chroot().path()

  @property
  def info(self):
    return self._pex_info

  @info.setter
  def info(self, value):
    if not isinstance(value, PexInfo):
      raise TypeError('PEXBuilder.info must be a PexInfo!')
    self._pex_info = value

  def add_source(self, filename, env_filename):
    self._chroot.link(filename, env_filename, "source")
    if filename.endswith('.py'):
      env_filename_pyc = os.path.splitext(env_filename)[0] + '.pyc'
      with open(filename) as fp:
        pyc_object = CodeMarshaller.from_py(fp.read(), env_filename)
      self._chroot.write(pyc_object.to_pyc(), env_filename_pyc, 'source')

  def add_resource(self, filename, env_filename):
    self._chroot.link(filename, env_filename, "resource")

  def add_requirement(self, req, dynamic=False, repo=None):
    self._pex_info.add_requirement(req, repo=repo, dynamic=dynamic)

  def set_entry_point(self, entry_point):
    self.info.entry_point = entry_point

  def add_egg(self, egg):
    """
      helper for add_distribution
    """
    if os.path.isdir(egg):
      metadata = PathMetadata(egg, os.path.join(egg, 'EGG-INFO'))
    else:
      metadata = EggMetadata(zipimporter(egg))
    dist = Distribution.from_filename(egg, metadata)
    self.add_distribution(dist)
    self.add_requirement(dist.as_requirement(), dynamic=False, repo=None)

  def add_distribution(self, dist):
    if not dist.location.endswith('.egg'):
      raise PEXBuilder.InvalidDependency('Non-egg dependencies not yet supported.')
    if os.path.isdir(dist.location):
      # walk and write
      for fn, content in DistributionHelper.walk(dist):
        self._chroot.write(content.read(),
            os.path.join(self._pex_info.internal_cache, os.path.basename(dist.location), fn))
    else:
      self._chroot.link(dist.location,
          os.path.join(self._pex_info.internal_cache, os.path.basename(dist.location)))

  def set_executable(self, filename, env_filename=None):
    if env_filename is None:
      env_filename = os.path.basename(filename)
    if self._chroot.get("executable"):
      raise PEXBuilder.InvalidExecutableSpecification(
          "Setting executable on a PEXBuilder that already has one!")
    self._chroot.link(filename, env_filename, "executable")
    entry_point = env_filename
    entry_point.replace(os.path.sep, '.')
    self._pex_info.entry_point = entry_point.rpartition('.')[0]

  def _prepare_inits(self):
    relative_digest = self._chroot.get("source")
    init_digest = set()
    for path in relative_digest:
      split_path = path.split(os.path.sep)
      for k in range(1, len(split_path)):
        sub_path = os.path.sep.join(split_path[0:k] + ['__init__.py'])
        if sub_path not in relative_digest and sub_path not in init_digest:
          self._chroot.write("__import__('pkg_resources').declare_namespace(__name__)",
              sub_path)
          init_digest.add(sub_path)

  def _prepare_manifest(self):
    self._chroot.write(self._pex_info.dump().encode('utf-8'), PexInfo.PATH, label='manifest')

  def _prepare_main(self):
    self._chroot.write(BOOTSTRAP_ENVIRONMENT, '__main__.py', label='main')

  # TODO(wickman) Ideally we include twitter.common.python and twitter.common-core via the eggs
  # rather than this hackish .bootstrap mechanism.  (Furthermore, we'll probably need to include
  # both a pkg_resources and lib2to3 version of pkg_resources.)
  def _prepare_bootstrap(self):
    """
      Write enough of distribute into the .pex .bootstrap directory so that
      we can be fully self-contained.
    """
    distribute = dist_from_egg(self._interpreter.distribute)
    for fn, content_stream in DistributionHelper.walk_data(distribute):
      # TODO(wickman)  Investigate if the omission of setuptools proper causes failures to
      # build eggs.
      if fn == 'pkg_resources.py':
        self._chroot.write(content_stream.read(),
            os.path.join(self.BOOTSTRAP_DIR, 'pkg_resources.py'), 'resource')
    libraries = (
      'twitter.common.python',
      'twitter.common.python.http',
    )
    for name in libraries:
      dirname = name.replace('twitter.common.python', '_twitter_common_python').replace('.', '/')
      provider = get_provider(name)
      if not isinstance(provider, DefaultProvider):
        mod = __import__(name, fromlist=['wutttt'])
        provider = ZipProvider(mod)
      for fn in provider.resource_listdir(''):
        if fn.endswith('.py'):
          self._chroot.write(provider.get_resource_string(name, fn),
            os.path.join(self.BOOTSTRAP_DIR, dirname, fn), 'resource')

  def freeze(self):
    if self._frozen:
      return
    self._prepare_inits()
    self._prepare_manifest()
    self._prepare_bootstrap()
    self._prepare_main()
    self._frozen = True

  def build(self, filename):
    self.freeze()
    try:
      os.unlink(filename + '~')
      self._logger.warn('Previous binary unexpectedly exists, cleaning: %s' % (filename + '~'))
    except OSError:
      # The expectation is that the file does not exist, so continue
      pass
    if os.path.dirname(filename):
      safe_mkdir(os.path.dirname(filename))
    with open(filename + '~', 'ab') as pexfile:
      assert os.path.getsize(pexfile.name) == 0
      pexfile.write(to_bytes('%s\n' % self._interpreter.identity.hashbang()))
    self._chroot.zip(filename + '~', mode='a')
    if os.path.exists(filename):
      os.unlink(filename)
    os.rename(filename + '~', filename)
    chmod_plus_x(filename)
