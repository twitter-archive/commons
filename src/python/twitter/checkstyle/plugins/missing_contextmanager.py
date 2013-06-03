# TODO(wickman)
#
# 1. open(foo) should always be done in a with context.
#
# 2. if you see acquire/release on the same variable in a particular ast
#    body, warn about context manager use.

import ast

from ..common import CheckstylePlugin


class MissingContextManager(CheckstylePlugin):
  def nits(self):
    with_contexts = set(self.iter_ast_types(ast.With))
    with_context_calls = set(node.context_expr for node in with_contexts
        if isinstance(node.context_expr, ast.Call))

    for call in self.iter_ast_types(ast.Call):
      if isinstance(call.func, ast.Name) and call.func.id == 'open' and (
          call not in with_context_calls):
        yield self.warning('T802', 'open() calls should be made within a contextmanager.', call)
