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

from .jvm_target import JvmTarget


class ExportableJvmLibrary(JvmTarget):
  """A baseclass for java targets that support being exported to an artifact repository."""

  def __init__(self,
               name,
               sources,
               provides=None,
               dependencies=None,
               excludes=None,
               exclusives=None):
    """
    :param string name: The name of this target, which combined with this
      build file defines the target :class:`twitter.pants.base.address.Address`.
    :param sources: A list of filenames representing the source code
      this library is compiled from.
    :type sources: list of strings
    :param provides:
      An optional Dependency object indicating the The ivy artifact to export.
    :param dependencies: List of :class:`twitter.pants.base.target.Target` instances
      this target depends on.
    :type dependencies: list of targets
    :param excludes: List of :class:`twitter.pants.targets.exclude.Exclude` instances
      to filter this target's transitive dependencies against.
    :param buildflags: Unused, and will be removed in a future release.
    """

    # It's critical that provides is set 1st since _provides() is called elsewhere in the
    # constructor flow.
    self._provides = provides

    super(ExportableJvmLibrary, self).__init__(
        name,
        sources,
        dependencies,
        excludes,
        exclusives=exclusives)

    self.add_labels('exportable')

  @property
  def provides(self):
    return self._provides
