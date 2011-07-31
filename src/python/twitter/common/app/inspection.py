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

import sys
import inspect

class Inspection(object):
  @staticmethod
  def _find_main_from_caller():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if 'main' in stack[fr_n][0].f_locals:
        return stack[fr_n][0].f_locals['main']
    return None

  @staticmethod
  def _print_stack_locals(out=sys.stderr):
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      print >> out, '--- frame %s ---\n' % fr_n
      for key in stack[fr_n][0].f_locals:
        print >> out, '  %s => %s' % (
          key, stack[fr_n][0].f_locals[key])

  @staticmethod
  def _find_main_module():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if 'main' in stack[fr_n][0].f_locals:
        return stack[fr_n][0].f_locals['__name__']
    return None
