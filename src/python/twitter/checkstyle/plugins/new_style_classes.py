import ast

from ..common import (
    CheckstylePlugin,
    ASTStyleError)


class NewStyleClasses(CheckstylePlugin):
  def nits(self):
    for node in ast.walk(self.python_file.tree):
      if isinstance(node, ast.ClassDef):
        if not node.bases:
          yield ASTStyleError(self.python_file, node, 'Classes must be new-style classes.')
