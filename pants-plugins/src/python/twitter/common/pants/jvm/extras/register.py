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

from pants.backend.jvm.tasks.checkstyle import Checkstyle
from pants.backend.jvm.tasks.scalastyle import Scalastyle
from pants.goal.task_registrar import TaskRegistrar as task


def register_goals():
  # We always want compile to finish with style checks.
  task(name='checkstyle', action=Checkstyle).install('compile')
  task(name='scalastyle', action=Scalastyle).install('compile')
