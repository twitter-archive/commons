# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

from ..common import CheckstylePlugin, Nit

from pyflakes.checker import Checker as FlakesChecker


class FlakeError(Nit):
  # TODO(wickman) There is overlap between this and Flake8 -- consider integrating
  # checkstyle plug-ins into the PEP8 tool directly so that this can be inherited
  # by flake8.
  CLASS_ERRORS = {
    'DuplicateArgument': 'F831',
    'ImportShadowedByLoopVar': 'F402',
    'ImportStarUsed': 'F403',
    'LateFutureImport': 'F404',
    'Redefined': 'F810',
    'RedefinedInListComp': 'F812',
    'RedefinedWhileUnused': 'F811',
    'UndefinedExport': 'F822',
    'UndefinedLocal': 'F823',
    'UndefinedName': 'F821',
    'UnusedImport': 'F401',
    'UnusedVariable': 'F841',
  }

  def __init__(self, python_file, flake_message):
    super(FlakeError, self).__init__(
        self.CLASS_ERRORS.get(flake_message.__class__.__name__, 'F999'),
        Nit.ERROR,
        python_file,
        flake_message.message % flake_message.message_args,
        flake_message.lineno)


class PyflakesChecker(CheckstylePlugin):
  """Detect common coding errors via the pyflakes package."""

  def nits(self):
    checker = FlakesChecker(self.python_file.tree, self.python_file.filename)
    for message in sorted(checker.messages, key=lambda msg: msg.lineno):
      yield FlakeError(self.python_file, message)
