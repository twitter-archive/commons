import ast

from ..common import CheckstylePlugin


class NewStyleClasses(CheckstylePlugin):
  def nits(self):
    for class_def in self.iter_ast_types(ast.ClassDef):
      if not class_def.bases:
        yield self.error('T606', 'Classes must be new-style classes.', class_def)
