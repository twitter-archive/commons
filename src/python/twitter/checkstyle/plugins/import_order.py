import ast
from distutils import sysconfig

from ..common import (
    ASTStyleError,
    CheckstylePlugin,
    StyleError)


# TODO(wickman)
#   - Warn if a package is marked as a 3rdparty but it's actually a package
#     in the current working directory that should be a package-absolute
#     import (i.e. from __future__ import absolute_imports)
class ImportOrder(CheckstylePlugin):
  PLATFORM_SPECIFIC_MODULE_PATH = sysconfig.get_python_lib(plat_specific=1)
  STANDARD_LIBRARY_MODULE_PATH = sysconfig.get_python_lib(standard_lib=1)
  NUMBERED_ORDERING = {
    'stdlib': 1,
    'twitter': 2,
    'gen': 3,
    'package': 4,
    '3rdparty': 5,
  }
  MODULE_CACHE = {}

  @classmethod
  def order_names(cls, import_order):
    reverse_ordering = dict((v, k) for (k, v) in cls.NUMBERED_ORDERING.items())
    return ' '.join(reverse_ordering.get(import_id, 'unclassified') for import_id in import_order)

  @classmethod
  def extract_import_modules(cls, node):
    if not isinstance(node, (ast.Import, ast.ImportFrom)):
      raise TypeError('classify_import only operates on ast.Import and ast.ImportFrom types.')
    if isinstance(node, ast.Import):
      return [alias.name for alias in node.names]
    elif isinstance(node, ast.ImportFrom):
      return [node.module]
    return []

  @classmethod
  def classify_import(cls, node, minimum_level=0):
    modules = []

    for module_name in cls.extract_import_modules(node):
      if module_name == '':
        # from . import foo
        modules.append(('package', '__init__'))
        continue
      if isinstance(node, ast.ImportFrom) and node.level > minimum_level:
        modules.append(('package', module_name))
        continue
      if module_name.startswith('twitter.'):
        modules.append(('twitter', module_name))
        continue
      if module_name.startswith('gen.'):
        modules.append(('gen', module_name))
        continue
      try:
        module = cls.MODULE_CACHE.get(module_name, __import__(module_name))
      except ImportError:
        modules.append(('3rdparty', module_name))
        continue
      if not hasattr(module, '__file__'):
        modules.append(('stdlib', module_name))
        continue
      # handle .pex exceptions first
      if '/.bootstrap/' in module.__file__ or '/.deps/' in module.__file__:
        modules.append(('3rdparty', module_name))
        continue
      if module.__file__.startswith(cls.PLATFORM_SPECIFIC_MODULE_PATH):
        modules.append(('3rdparty', module_name))
        continue
      if module.__file__.startswith(cls.STANDARD_LIBRARY_MODULE_PATH):
        modules.append(('stdlib', module_name))
        continue
      modules.append(('unclassifiable', module_name))

    return set(modules)

  def nits(self):
    _, errors = self.check_imports(self.python_file.tree)
    return errors

  def validate_import(self, node):
    errors = []
    if not isinstance(node, (ast.Import, ast.ImportFrom)):
      raise TypeError('validate_import only operates on ast.Import and ast.ImportFrom types.')
    if isinstance(node, ast.ImportFrom):
      if len(node.names) == 1 and node.names[0].name == '*':
        errors.append(ASTStyleError(self.python_file, node, 'Wildcard imports are not allowed.'))
      names = [alias.name.lower() for alias in node.names]
      if names != sorted(names):
        errors.append(
            ASTStyleError(self.python_file, node,
                          'From import must import names in lexical order.'))
    if isinstance(node, ast.Import):
      if len(node.names) > 1:
        errors.append(
            ASTStyleError(self.python_file, node,
                          'Absolute import statements should only import one module at a time.'))
    return node, errors

  def classify_imports(self, chunk, minimum_level=0):
    """
      Possible import statements:

      import name
      from name import subname
      from name import subname1 as subname2
      from name import *
      from name import tuple

      AST representations:

      ImportFrom:
         module=name
         names=[alias(name, asname), ...]
                    name can be '*'

      Import:
        names=[alias(name, asname), ...]

      Imports are classified into 5 classes:
        stdlib      => Python standard library
        twitter.*   => Twitter internal / standard library
        gen.*       => Thrift gen namespaces
        .*          => Package-local imports
        3rdparty    => site-packages or third party

      classify_imports classifies the import into one of these forms.
    """
    errors = []
    all_module_types = set()
    for node in chunk:
      _, node_errors = self.validate_import(node)
      errors.extend(node_errors)
      module_names = self.classify_import(node, minimum_level)
      module_types = set(module_type for module_type, module_name in module_names)
      if len(module_types) > 1:
        errors.append(ASTStyleError(self.python_file, node,
            'Import statement imports from multiple module types: %s.' % ', '.join(module_types)))
      if 'unclassifiable' in module_types:
        errors.append(ASTStyleError(self.python_file, node, 'Unclassifiable import.'))
      all_module_types.update(module_types)
    if len(chunk) > 0 and len(all_module_types) > 1:
      errors.append(
          StyleError(self.python_file,
                    'Import block starting here contains imports from multiple module types: %s.'
                       % ', '.join(all_module_types),
                    chunk[0].lineno))
    return all_module_types, errors

  def check_imports(self, tree):
    if not isinstance(tree, ast.Module):
      raise TypeError('Expected tree to be of type ast.Module, got %s' % type(tree))

    # NB: For some reason the Python AST will occasionally give an
    # off-by-one error for ImportFrom levels in a single file.  This means
    # we can't just rely upon "node.level > 0" to determine if a package is
    # a package-relative import, but instead node.level > min(all levels)
    #
    # this means we will miscategorize "from .foo import bar" if it is the only
    # import in a file, but in that case the import ordering does not matter.
    levels = [node.level for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)]
    minimum_level = min(levels or [0])

    chunk = []
    errors = []
    module_order = []
    last_line = None

    def check_chunk():
      if chunk:
        module_types, chunk_errors = self.classify_imports(chunk, minimum_level)
        errors.extend(chunk_errors)
        module_order.append(list(module_types))
        del chunk[:]

    # XXX(wickman) This heuristic is broken.  We need to check over logical lines rather
    # than physical lines.
    for leaf in tree.body:
      if isinstance(leaf, (ast.Import, ast.ImportFrom)):
        if last_line and leaf.lineno == last_line + 1:
          chunk.append(leaf)
        else:
          check_chunk()
        last_line = leaf.lineno
      else:
        check_chunk()

    numbered_module_order = []
    for modules in module_order:
      if len(modules) == 1:
        if modules[0] in self.NUMBERED_ORDERING:
          numbered_module_order.append(self.NUMBERED_ORDERING[modules[0]])

    if numbered_module_order != sorted(numbered_module_order):
      errors.append(ASTStyleError(self.python_file, tree,
          'Out of order import chunks: Got %s and expect %s.' % (
          self.order_names(numbered_module_order),
          self.order_names(sorted(numbered_module_order)))))

    return tree, errors
