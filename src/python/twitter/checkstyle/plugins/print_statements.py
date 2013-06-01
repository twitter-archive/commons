import ast
import re

from ..common import (
    CheckstylePlugin,
    ASTStyleError)


class PrintStatements(CheckstylePlugin):
  FUNCTIONY_EXPRESSION = re.compile(r'^\s*\(.*\)\s*$')

  def nits(self):
    for ast_node in ast.walk(self.python_file.tree):
      # In Python 3.x and in 2.x with __future__ print_function, prints show up as plain old
      # function expressions.  ast.Print does not exist in Python 3.x.  However, allow use
      # syntactically as a function, i.e. ast.Print but with ws "(" .* ")" ws
      if isinstance(ast_node, ast.Print):
        logical_line = ''.join(self.python_file[ast_node.lineno])
        print_offset = logical_line.index('print')
        stripped_line = logical_line[print_offset + len('print'):]
        if not self.FUNCTIONY_EXPRESSION.match(stripped_line):
          yield ASTStyleError(self.python_file, ast_node, "Print used as a statement.")
