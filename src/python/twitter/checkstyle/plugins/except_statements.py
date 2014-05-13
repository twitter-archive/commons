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


class ExceptStatements(CheckstylePlugin):
  """Do not allow non-3.x-compatible and/or dangerous except statements."""

  @classmethod
  def blanket_excepts(cls, node):
    for handler in node.handlers:
      if handler.type is None and handler.name is None:
        return handler

  @classmethod
  def iter_excepts(cls, tree):
    for ast_node in ast.walk(tree):
      if isinstance(ast_node, ast.TryExcept):
        yield ast_node

  def nits(self):
    for try_except in self.iter_excepts(self.python_file.tree):
      # Check case 1, blanket except
      handler = self.blanket_excepts(try_except)
      if handler:
        yield self.error('T803', 'Blanket except: not allowed.', handler)

      # Check case 2, except Foo, bar:
      for handler in try_except.handlers:
        statement = ''.join(self.python_file[handler.lineno])
        except_index = statement.index('except')
        except_suffix = statement[except_index + len('except'):]

        if handler.name and ' as ' not in except_suffix:
          yield self.error('T601', 'Old-style except statements forbidden.', handler)
