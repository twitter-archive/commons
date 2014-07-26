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

from pants.backend.core.tasks.what_changed import ScmWhatChanged
from pants.backend.jvm.tasks.checkstyle import Checkstyle
from pants.goal.goal import Goal
from pants.goal.phase import Phase


def register_goals():
  changed = Phase('changed').with_description('Print the targets changed since some prior commit.')
  changed.install(Goal(name='changed', action=ScmWhatChanged))

  # We always want compile to finish with a checkstyle
  compile = Phase('compile')
  compile.install(Goal(name='checkstyle', action=Checkstyle,
                       dependencies=['gen', 'resolve']))
