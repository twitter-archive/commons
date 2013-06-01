import ast
from functools import wraps
import keyword
import re

from twitter.common.lang import Compatibility

from ..common import (
    ASTStyleError,
    CheckstylePlugin,
    StyleError)


ALL_LOWER_CASE_RE = re.compile(r'^[a-z][a-z\d]*$')
ALL_UPPER_CASE_RE = re.compile(r'^[A-Z][A-Z\d]+$')
LOWER_SNAKE_RE = re.compile(r'^([a-z][a-z\d]*)(_[a-z\d]+)*$')
UPPER_SNAKE_RE = re.compile(r'^([A-Z][A-Z\d]*)(_[A-Z\d]+)*$')
UPPER_CAMEL_RE = re.compile(r'^([A-Z][a-z\d]*)+$')
RESERVED_NAMES = frozenset(keyword.kwlist)


if Compatibility.PY2:
  import __builtin__
  BUILTIN_NAMES = dir(__builtin__)
else:
  import builtin
  BUILTIN_NAMES = dir(builtin)


def allow_underscores(num):
  def wrap(function):
    @wraps(function)
    def wrapped_function(name):
      if name.startswith('_' * (num + 1)):
        return False
      return function(name.lstrip('_'))
    return wrapped_function
  return wrap


@allow_underscores(1)
def is_upper_camel(name):
  """UpperCamel, AllowingHTTPAbbrevations, _WithUpToOneUnderscoreAllowable."""
  return bool(UPPER_CAMEL_RE.match(name) and not ALL_UPPER_CASE_RE.match(name))


@allow_underscores(2)
def is_lower_snake(name):
  """lower_snake_case, _with, __two_underscores_allowable."""
  return LOWER_SNAKE_RE.match(name) is not None


def is_reserved_name(name):
  return name in BUILTIN_NAMES or name in RESERVED_NAMES


def is_reserved_with_trailing_underscore(name):
  """For example, super_, id_, type_"""
  if name.endswith('_') and not name.endswith('__'):
    return is_reserved_name(name[:-1])
  return False


def is_builtin_name(name):
  """For example, __foo__ or __bar__."""
  if name.startswith('__') and name.endswith('__'):
    return ALL_LOWER_CASE_RE.match(name[2:-2]) is not None
  return False


@allow_underscores(2)
def is_constant(name):
  return UPPER_SNAKE_RE.match(name) is not None


class PEP8VariableNames(CheckstylePlugin):
  """Enforces PEP8 recommendations for variable names.

  Specifically:
     UpperCamel class names
     lower_snake / _lower_snake / __lower_snake function names
     lower_snake expression variable names
     CLASS_LEVEL_CONSTANTS = {}
     GLOBAL_LEVEL_CONSTANTS = {}

  Also within classes, if you see:
    class Distiller(object):
      CONSTANT = "Foo"
      def foo(self, value):
         return os.path.join(Distiller.CONSTANT, value)

  recommend using self.CONSTANT instead of Distiller.CONSTANT as otherwise
  it makes subclassing impossible.
  """

  CLASS_GLOBAL_BUILTINS = frozenset((
    '__slots__',
    '__metaclass__',
  ))

  def iter_class_methods(self, class_node):
    for node in class_node.body:
      if isinstance(node, ast.FunctionDef):
        yield node

  def iter_class_globals(self, class_node):
    for node in class_node.body:
      # TODO(wickman) Occasionally you have the pattern where you set methods equal to each other
      # which should be allowable, for example:
      #   class Foo(object):
      #     def bar(self):
      #       pass
      #     alt_bar = bar
      if isinstance(node, ast.Assign):
        for name in node.targets:
          if isinstance(name, ast.Name):
            yield name

  def nits(self):
    class_methods = set()
    all_methods = set(function_def for function_def in ast.walk(self.python_file.tree)
        if isinstance(function_def, ast.FunctionDef))

    for class_def in self.iter_ast_types(ast.ClassDef):
      if not is_upper_camel(class_def.name):
        yield StyleError(self.python_file, 'Classes must be UpperCamelCased', class_def.lineno)
      for class_global in self.iter_class_globals(class_def):
        if not is_constant(class_global.id) and class_global.id not in self.CLASS_GLOBAL_BUILTINS:
          yield StyleError(self.python_file, 'Class globals must be UPPER_SNAKE_CASED',
              class_global.lineno)
      class_methods.update(self.iter_class_methods(class_def))

    for function_def in all_methods - class_methods:
      if is_reserved_name(function_def.name):
        yield ASTStyleError(self.python_file, function_def, 'Method name overrides a builtin.')

    for function_def in all_methods:
      if not any((is_lower_snake(function_def.name),
                  is_builtin_name(function_def.name),
                  is_reserved_with_trailing_underscore(function_def.name))):
        yield ASTStyleError(self.python_file, function_def,
            'Method names must be lower_snake_cased')
