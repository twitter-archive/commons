from collections import defaultdict
import tokenize
import sys

from ..common import (
    CheckstylePlugin,
    StyleError)


class TrailingWhitespace(CheckstylePlugin):
  # TODO(wickman) This needs a test
  @classmethod
  def build_exception_map(cls, tokens):
    """Generates a set of ranges where we accept trailing slashes, specifically within comments
       and strings."""
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

  def has_exception(self, line_number, slash_position):
    for start, end in self._exception_map.get(line_number, ()):
      if start <= slash_position < end:
        return True
    return False

  def nits(self):
    for line_number, line in self.python_file.enumerate():
      if line.rstrip() != line:
        yield StyleError(self.python_file, "Line has trailing whitespace.", line_number)
      if line.rstrip().endswith('\\'):
        if not self.has_exception(line_number, len(line.rstrip()) - 1):
          yield StyleError(self.python_file, "Line has trailing slashes.", line_number)
