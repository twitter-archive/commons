import ast
import textwrap

from twitter.checkstyle.common import (
    ASTStyleError,
    ASTStyleWarning,
    Nit,
    PythonFile,
    StyleError,
    StyleWarning)

import pytest


def make_statement(statement):
  return '\n'.join(textwrap.dedent(statement).splitlines()[1:])


PYTHON_STATEMENT = make_statement("""
import ast
from os.path import (
    join,
    split,
)

import zookeeper


class Keeper(object):
  def __init__(self):
    pass
""")


def test_python_file():
  pf = PythonFile(PYTHON_STATEMENT, 'keeper.py')
  assert pf.filename == 'keeper.py'
  assert pf.logical_lines == {
    1: (1, 2, 0),
    2: (2, 6, 0),
    7: (7, 8, 0),
    10: (10, 11, 0),
    11: (11, 12, 2),
    12: (12, 13, 4)
  }
  with pytest.raises(IndexError):
    pf[0]
  with pytest.raises(IndexError):
    pf[len(PYTHON_STATEMENT.splitlines()) + 1]
  assert pf[1] == ["import ast"]
  assert pf[2] == ["from os.path import (", "    join,", "    split,", ")"]
  assert pf[3] == ["    join,"]
  assert '\n'.join(pf) == PYTHON_STATEMENT
  assert list(pf.enumerate()) == list(enumerate(PYTHON_STATEMENT.splitlines(), 1))


def test_style_error():
  pf = PythonFile(PYTHON_STATEMENT, 'keeper.py')
  se = StyleError(pf, 'You have a terrible taste in libraries.')
  assert se.line_number is None
  str(se)
  se = StyleError(pf, 'You have a terrible taste in libraries.', 7)
  assert se.line_number == '007'
  str(se)
  se = StyleError(pf, 'You have a terrible taste in libraries.', 2)
  assert se.line_number == '002-005'
  str(se)
  assert se.severity == Nit.ERROR
  sw = StyleWarning(pf, 'You have a terrible taste in libraries.', 2)
  assert sw.severity == Nit.WARNING


def test_ast_style_error():
  pf = PythonFile(PYTHON_STATEMENT, 'keeper.py')
  import_from = None
  for node in ast.walk(pf.tree):
    if isinstance(node, ast.ImportFrom):
      import_from = node
  assert import_from is not None
  ase = ASTStyleError(pf, import_from, "I don't like your from import!")
  assert ase.severity == Nit.ERROR
  se = StyleError(pf, "I don't like your from import!", 2)
  assert str(se) == str(ase)
  sw = ASTStyleWarning(pf, "I don't like your from import!", 2)
  assert sw.severity == Nit.WARNING
