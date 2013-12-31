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

from functools import partial
import os

from twitter.pants.base.build_environment import get_buildroot


class SourceRoot(object):
  """Allows registration of a source root for a set of targets.

  A source root is the base path sources for a particular language are found relative to.
  Generally compilers or interpreters for the source will expect sources relative to a base path
  and a source root allows calculation of the correct relative paths.

  It is illegal to have nested source roots.
  """
  _ROOTS = set()

  @staticmethod
  def _register(sourceroot):
    SourceRoot._ROOTS.add(sourceroot.basedir)

  @staticmethod
  def find(target):
    """Finds the source root for the given target.

    If none is registered, returns the parent directory of the target's BUILD file.
    """
    target_path = os.path.relpath(target.address.buildfile.parent_path, get_buildroot())
    for root in SourceRoot._ROOTS:
      if target_path.startswith(root):
        return root
    return target_path

  @staticmethod
  def register(basedir):
    """Registers the given basedir as a source root for the given target types."""
    return SourceRoot(basedir)

  @classmethod
  def lazy_rel_source_root(cls, reldir):
    return partial(cls, reldir=reldir)

  # TODO; Remove *args when we've fixed all source_root() calls.
  def __init__(self, basedir, *args, **kwargs):
    """Initializes a source root at basedir for the given target types.

    :basedir The base directory to resolve sources relative to
    """
    reldir = kwargs.pop('reldir', get_buildroot())
    basepath = os.path.abspath(os.path.join(reldir, basedir))
    if get_buildroot() != os.path.commonprefix((basepath, get_buildroot())):
      raise ValueError('The supplied basedir %s is not a sub-path of the project root %s' % (
        basepath,
        get_buildroot()
      ))

    self.basedir = os.path.relpath(basepath, get_buildroot())
    SourceRoot._register(self)
