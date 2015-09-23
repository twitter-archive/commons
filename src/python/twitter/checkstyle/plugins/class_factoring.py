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


class ClassFactoring(CheckstylePlugin):
  """Enforces recommendations for accessing class attributes.

  Within classes, if you see:
    class Distiller(object):
      CONSTANT = "Foo"
      def foo(self, value):
         return os.path.join(Distiller.CONSTANT, value)

  recommend using self.CONSTANT instead of Distiller.CONSTANT as otherwise
  it makes subclassing impossible."""

  def iter_class_accessors(self, class_node):
    for node in ast.walk(class_node):
      if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name) and (
          node.value.id == class_node.name):
        yield node

  def nits(self):
    for class_def in self.iter_ast_types(ast.ClassDef):
      for node in self.iter_class_accessors(class_def):
        yield self.warning('T800',
            'Instead of %s.%s use self.%s or cls.%s with instancemethods and classmethods '
            'respectively.' % (class_def.name, node.attr, node.attr, node.attr),
            node)
