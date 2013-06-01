from __future__ import absolute_import

from ..common import (
    CheckstylePlugin,
    PythonFile,
    StyleError)

import pep8


class PEP8Error(StyleError):
  def __init__(self, python_file, code, line_number, offset, text, doc):
    super(PEP8Error, self).__init__(python_file, text, line_number)


class TwitterReporter(pep8.BaseReport):
  def init_file(self, filename, lines, expected, line_offset):
    super(TwitterReporter, self).init_file(filename, lines, expected, line_offset)
    self._python_file = PythonFile.parse(filename)
    self._twitter_errors = []

  def error(self, line_number, offset, text, check):
    code = super(TwitterReporter, self).error(line_number, offset, text, check)
    if code:
      self._twitter_errors.append(
          PEP8Error(self._python_file, code, line_number, offset, text[5:], check.__doc__))
    return code

  @property
  def twitter_errors(self):
    return self._twitter_errors


# TODO(wickman) Consider killing the continuation_line_indentation check or submit a pull-
# request to pep8 to allow for indentation levels other than 4.
#
# TODO(wickman) Classify each of the PEP8 nits by COMMENT/WARNING/ERROR
class PEP8Checker(CheckstylePlugin):
  STYLE_GUIDE = pep8.StyleGuide(
      max_line_length=100,
      # indent_level=2,
      ignore=[
          'E111',  # indent should be multiple of four
          'E121',  # indent should be a multiple of four
          'E125',  # continuation line does not distinguish itself from next logical line
                   #   (uses indent == 4, so it breaks for twitter)
          'E127',  # continuation line over-indented for visual indent
          'E128',  # continuation line under-indented for visual indent
          'E701',  # class Error(Exception): pass
      ],
      verbose=False,
      reporter=TwitterReporter
  )

  def nits(self):
    report = self.STYLE_GUIDE.check_files([self.python_file.filename])
    return report.twitter_errors
