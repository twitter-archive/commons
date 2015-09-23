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

from collections import defaultdict
import tokenize
import sys

from ..common import CheckstylePlugin


class TrailingWhitespace(CheckstylePlugin):
  """Warn on invalid trailing whitespace."""

  @classmethod
  def build_exception_map(cls, tokens):
    """Generates a set of ranges where we accept trailing slashes, specifically within comments
       and strings.
    """
    exception_ranges = defaultdict(list)
    for token in tokens:
      token_type, _, token_start, token_end = token[0:4]
      if token_type in (tokenize.COMMENT, tokenize.STRING):
        if token_start[0] == token_end[0]:
          exception_ranges[token_start[0]].append((token_start[1], token_end[1]))
        else:
          exception_ranges[token_start[0]].append((token_start[1], sys.maxint))
          for line in range(token_start[0] + 1, token_end[0]):
            exception_ranges[line].append((0, sys.maxint))
          exception_ranges[token_end[0]].append((0, token_end[1]))
    return exception_ranges

  def __init__(self, *args, **kw):
    super(TrailingWhitespace, self).__init__(*args, **kw)
    self._exception_map = self.build_exception_map(self.python_file.tokens)

  def has_exception(self, line_number, exception_start, exception_end=None):
    exception_end = exception_end or exception_start
    for start, end in self._exception_map.get(line_number, ()):
      if start <= exception_start and exception_end <= end:
        return True
    return False

  def nits(self):
    for line_number, line in self.python_file.enumerate():
      stripped_line = line.rstrip()
      if stripped_line != line and not self.has_exception(line_number,
          len(stripped_line), len(line)):
        yield self.error('T200', 'Line has trailing whitespace.', line_number)
      if line.rstrip().endswith('\\'):
        if not self.has_exception(line_number, len(line.rstrip()) - 1):
          yield self.error('T201', 'Line has trailing slashes.', line_number)
