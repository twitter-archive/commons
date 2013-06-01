from __future__ import absolute_import

from ..common import (
    CheckstylePlugin,
    StyleError)

from pyflakes.checker import Checker as FlakesChecker


class FlakeError(StyleError):
  def __init__(self, python_file, flake_message):
    super(FlakeError, self).__init__(
        python_file, flake_message.message % flake_message.message_args, flake_message.lineno)


class PyflakesChecker(CheckstylePlugin):
  def nits(self):
    checker = FlakesChecker(self.python_file.tree, self.python_file.filename)
    for message in sorted(checker.messages, key=lambda msg: msg.lineno):
      yield FlakeError(self.python_file, message)
