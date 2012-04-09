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

from __future__ import print_function

import inspect
import os
import sys

class Inspection(object):
  class InternalError(Exception): pass

  # TODO(wickman)
  #   Remove all calls to inspect.stack().  This is just bad.  Port everything over
  #   to iterating from currentframe => outer frames.
  @staticmethod
  def find_main_from_caller():
    last_frame = inspect.currentframe()
    while True:
      inspect_frame = last_frame.f_back
      if not inspect_frame:
        break
      if 'main' in inspect_frame.f_locals:
        return inspect_frame.f_locals['main']
      last_frame = inspect_frame
    raise Inspection.InternalError("Unable to detect main from the stack!")

  @staticmethod
  def print_stack_locals(out=sys.stderr):
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      print('--- frame %s ---\n' % fr_n, file=out)
      for key in stack[fr_n][0].f_locals:
        print('  %s => %s' % (key, stack[fr_n][0].f_locals[key]), file=out)

  @staticmethod
  def find_main_module():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if 'main' in stack[fr_n][0].f_locals:
        return stack[fr_n][0].f_locals['__name__']
    return None

  @staticmethod
  def get_main_locals():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if '__name__' in stack[fr_n][0].f_locals and (
          stack[fr_n][0].f_locals['__name__'] == '__main__'):
        return stack[fr_n][0].f_locals
    return {}

  @staticmethod
  def find_calling_module():
    last_frame = inspect.currentframe()
    while True:
      inspect_frame = last_frame.f_back
      if not inspect_frame:
        break
      if '__name__' in inspect_frame.f_locals:
        return inspect_frame.f_locals['__name__']
      last_frame = inspect_frame
    raise Inspection.InternalError("Unable to interpret stack frame!")

  @staticmethod
  def find_application_name():
    __entry_point__ = None
    locals = Inspection.get_main_locals()
    if '__file__' in locals and locals['__file__'] is not None:
      __entry_point__ = locals['__file__']
    elif '__loader__' in locals:
      from zipimport import zipimporter
      from pkgutil import ImpLoader
      # TODO(wickman) The monkeypatched zipimporter should probably not be a function
      # but instead a properly delegating proxy.
      if hasattr(locals['__loader__'], 'archive'):
        # assuming it ends in .zip or .egg, it may be of package format, so
        # foo-version-py2.6-arch.egg, so split off anything after '-'.
        __entry_point__ = os.path.basename(locals['__loader__'].archive)
        __entry_point__ = __entry_point__.split('-')[0].split('.')[0]
      elif isinstance(locals['__loader__'], ImpLoader):
        __entry_point__ = locals['__loader__'].get_filename()
    else:
      __entry_point__ = '__interpreter__'
    app_name = os.path.basename(__entry_point__)
    return app_name.split('.')[0]
