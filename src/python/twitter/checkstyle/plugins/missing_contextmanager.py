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

# TODO(wickman)
#
# 1. open(foo) should always be done in a with context.
#
# 2. if you see acquire/release on the same variable in a particular ast
#    body, warn about context manager use.

import ast

from ..common import CheckstylePlugin


class MissingContextManager(CheckstylePlugin):
  """Recommend the use of contextmanagers when it seems appropriate."""

  def nits(self):
    with_contexts = set(self.iter_ast_types(ast.With))
    with_context_calls = set(node.context_expr for node in with_contexts
        if isinstance(node.context_expr, ast.Call))

    for call in self.iter_ast_types(ast.Call):
      if isinstance(call.func, ast.Name) and call.func.id == 'open' and (
          call not in with_context_calls):
        yield self.warning('T802', 'open() calls should be made within a contextmanager.', call)
