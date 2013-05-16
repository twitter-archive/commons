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

import time

from twitter.common.threading.stoppable_thread import StoppableThread


class PeriodicThread(StoppableThread):
  """A thread that runs a target function periodically.

  Note: Don't subclass this to override run(). That won't work. """
  def __init__(self, group=None, target=None, name=None, period_secs=1, args=(), kwargs=None):
    if kwargs is None:
      kwargs = {}

    def _periodic_target():
      target(*args, **kwargs)
      time.sleep(period_secs)

    StoppableThread.__init__(self, group=group, target=_periodic_target, name=name, args=args, kwargs=kwargs)
