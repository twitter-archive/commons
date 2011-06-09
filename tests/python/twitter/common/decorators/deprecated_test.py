# ==================================================================================================
# Copyright 2011 Twitter, Inc.
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

import logging
from twitter.common.decorators import deprecated, deprecated_with_warning

import pytest
import unittest

# TODO(Brian Wickman): This should probably be split out into a test
# framework (and tested in and of itself.)
class TestLogHandler(logging.Handler):
  def __init__(self, level):
    logging.Handler.__init__(self)
    self._level = level
    self._records = []

  def emit(self, record):
    if record.levelno == self._level:
      self._records.append(record.msg)

  def records(self):
    return self._records[:]

  def clear(self):
    del self._records[:]

class TestDeprecatedDecorator(unittest.TestCase):
  @classmethod
  def setup_class(cls):
    cls._log_handler = TestLogHandler(logging.WARNING)
    root_logger = logging.getLogger()
    root_logger.addHandler(cls._log_handler)

  @classmethod
  def teardown_class(cls):
    root_logger = logging.getLogger()
    root_logger.removeHandler(cls._log_handler)

  def setUp(self):
    self._log_handler.clear()

  def test_deprecated_raises_on_nonfunction(self):
    with pytest.raises(ValueError):
      deprecated('high five!')

  def test_deprecated_raises_on_nil(self):
    with pytest.raises(ValueError):
      deprecated(None)

  def test_deprecated_logs_warning(self):
    """Tests that defining @deprecated doesn't log as well."""
    assert self._log_handler.records() == []
    @deprecated
    def chips_ahoy():
      pass
    assert self._log_handler.records() == []
    chips_ahoy()
    records = self._log_handler.records()
    assert len(records) == 1
    assert records[0].startswith('DEPRECATION WARNING:')

  def test_deprecated_takes_warning_message(self):
    warning_message = "Use oreos() instead!"
    @deprecated_with_warning(warning_message)
    def chips_ahoy():
      pass
    chips_ahoy()
    records = self._log_handler.records()
    assert len(records) == 1
    assert records[0].startswith('DEPRECATION WARNING:')
    assert records[0].endswith(warning_message)

  def test_deprecated_handles_args(self):
    warning_message = "Use oreos() instead!"
    @deprecated_with_warning(warning_message)
    def chips_ahoy(a,b):
      pass
    chips_ahoy(1,2)
    records = self._log_handler.records()
    assert len(records) == 1
    assert records[0].startswith('DEPRECATION WARNING:')
    assert records[0].endswith(warning_message)

  def test_deprecated_handles_kwargs(self):
    warning_message = "Use oreos() instead!"
    @deprecated_with_warning(warning_message)
    def chips_ahoy(a=None,b=4):
      pass
    chips_ahoy(a=3)
    records = self._log_handler.records()
    assert len(records) == 1
    assert records[0].startswith('DEPRECATION WARNING:')
    assert records[0].endswith(warning_message)

  def test_deprecated_handles_args_and_kwargs(self):
    warning_message = "Use oreos() instead!"
    @deprecated_with_warning(warning_message)
    def chips_ahoy(a,b=4):
      pass
    chips_ahoy(15)
    records = self._log_handler.records()
    assert len(records) == 1
    assert records[0].startswith('DEPRECATION WARNING:')
    assert records[0].endswith(warning_message)
