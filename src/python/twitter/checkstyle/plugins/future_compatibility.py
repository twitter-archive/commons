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

# Warn on non 2.x/3.x compatible symbols:
#   - basestring
#   - xrange
#
# Methods:
#   - .iteritems
#   - .iterkeys
#
# Comprehension builtins
#   - filter
#   - map
#   - range
#
#   => Make sure that these are not assigned.
#   Warn if they are assigned or returned directly from functions
#
# Class internals:
#   __metaclass__


import ast

from ..common import CheckstylePlugin


class FutureCompatibility(CheckstylePlugin):
  """Warns about behavior that will likely break when moving to Python 3.x"""
  BAD_ITERS = frozenset(('iteritems', 'iterkeys', 'itervalues'))
  BAD_FUNCTIONS = frozenset(('xrange',))
  BAD_NAMES = frozenset(('basestring', 'unicode'))

  def nits(self):
    for call in self.iter_ast_types(ast.Call):
      if isinstance(call.func, ast.Attribute):
        if call.func.attr in self.BAD_ITERS:
          yield self.error(
              'T602', '%s disappears in Python 3.x.  Use non-iter instead.' % call.func.attr, call)
      elif isinstance(call.func, ast.Name):
        if call.func.id in self.BAD_FUNCTIONS:
          yield self.error(
              'T603', 'Please avoid %s as it disappears in Python 3.x.' % call.func.id, call)
    for name in self.iter_ast_types(ast.Name):
      if name.id in self.BAD_NAMES:
        yield self.error(
            'T604', 'Please avoid %s as it disappears in Python 3.x.' % name.id, name)
    for class_def in self.iter_ast_types(ast.ClassDef):
      for node in class_def.body:
        if not isinstance(node, ast.Assign):
          continue
        for name in node.targets:
          if not isinstance(name, ast.Name):
            continue
          if name.id == '__metaclass__':
            yield self.warning('T605',
                'This metaclass style is deprecated and gone entirely in Python 3.x.', name)
