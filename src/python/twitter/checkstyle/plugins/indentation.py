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

import tokenize

from ..common import CheckstylePlugin


# TODO(wickman) Update this to sanitize line continuation styling as we have
# disabled it from pep8.py due to mismatched indentation styles.
class Indentation(CheckstylePlugin):
  """Enforce proper indentation."""
  INDENT_LEVEL = 2  # the one true way

  def nits(self):
    indents = []

    for token in self.python_file.tokens:
      token_type, token_text, token_start = token[0:3]
      if token_type is tokenize.INDENT:
        last_indent = len(indents[-1]) if indents else 0
        current_indent = len(token_text)
        if current_indent - last_indent != self.INDENT_LEVEL:
          yield self.error('T100',
              'Indentation of %d instead of %d' % (current_indent - last_indent, self.INDENT_LEVEL),
              token_start[0])
        indents.append(token_text)
      elif token_type is tokenize.DEDENT:
        indents.pop()
