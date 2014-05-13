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

import ast

from ..common import CheckstylePlugin


class Newlines(CheckstylePlugin):
  def iter_toplevel_defs(self):
    for node in self.python_file.tree.body:
      if isinstance(node, ast.FunctionDef) or isinstance(node, ast.ClassDef):
        yield node

  def previous_blank_lines(self, line_number):
    blanks = 0
    while line_number > 1:
      line_number -= 1
      line_value = self.python_file.lines[line_number].strip()
      if line_value.startswith('#'):
        continue
      if line_value:
        break
      blanks += 1
    return blanks

  def nits(self):
    for node in self.iter_toplevel_defs():
      previous_blank_lines = self.previous_blank_lines(node.lineno)
      if node.lineno > 2 and previous_blank_lines != 2:
        yield self.error('T302', 'Expected 2 blank lines, found %d' % previous_blank_lines,
            node)
    for node in self.iter_ast_types(ast.ClassDef):
      for subnode in node.body:
        if not isinstance(subnode, ast.FunctionDef):
          continue
        previous_blank_lines = self.previous_blank_lines(subnode.lineno)
        if subnode.lineno - node.lineno > 1 and previous_blank_lines != 1:
          yield self.error('T301', 'Expected 1 blank lines, found %d' % previous_blank_lines,
              subnode)
