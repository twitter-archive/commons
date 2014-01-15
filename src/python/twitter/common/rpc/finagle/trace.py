# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

import copy
from contextlib import contextmanager
import random
import re

class SpanId(object):
  __slots__ = ('_value',)

  HEX_REGEX = re.compile(r'^[a-f0-9]{16}$', re.IGNORECASE)

  class InvalidSpanId(ValueError):
    def __init__(self, value):
      ValueError.__init__(self, 'Invalid SpanId: %s' % repr(value))

  @staticmethod
  def from_value(value):
    if isinstance(value, str):
      if SpanId.HEX_REGEX.match(value):
        return SpanId(int(value, 16))
    elif isinstance(value, (int, long)):
      return SpanId(value)
    elif isinstance(value, SpanId):
      return SpanId(value.value)
    elif value is None:
      return SpanId(None)
    raise SpanId.InvalidSpanId(value)

  def __init__(self, value):
    self._value = value

  @property
  def value(self):
    return self._value

  def __str__(self):
    return 'SpanId(%016x)' % (self._value if self._value is not None else 'Empty')


class TraceId(object):
  @staticmethod
  def rand():
    return random.randint(0, 2**63-1)

  def __init__(self, trace_id, parent_id, span_id, sampled):
    self.trace_id = SpanId.from_value(trace_id)
    self.parent_id = SpanId.from_value(parent_id)
    self.span_id = SpanId.from_value(span_id)
    self.sampled = bool(sampled)

  def next(self):
    return TraceId(self.trace_id, self.span_id, TraceId.rand(), self.sampled)

  def __str__(self):
    return 'TraceId(trace_id = %s, parent_id = %s, span_id = %s, sampled = %s)' % (
      self.trace_id, self.parent_id, self.span_id, self.sampled)


class Trace(object):
  """
    The container of a trace.  Typically stored as a threadlocal on each
    finagle-upgraded protocol.
  """
  def __init__(self, sample_rate=0.001):
    assert 0.0 <= sample_rate <= 1.0
    self._sample_rate = sample_rate
    self._stack = []

  def get(self):
    if len(self._stack) == 0:
      span_id = TraceId.rand()
      trace_id = TraceId(span_id, None, span_id, self.should_sample())
      self._stack.append(trace_id)
    return self._stack[-1]

  @contextmanager
  def push(self, trace_id):
    self._stack.append(trace_id)
    try:
      yield self
    finally:
      self._stack.pop()

  @contextmanager
  def unwind(self):
    trace_id_copy = copy.deepcopy(self._stack[-1])
    try:
      yield self
    finally:
      self._stack[-1] = trace_id_copy

  def pop(self):
    return self._stack.pop()

  def should_sample(self):
    return random.random() < self._sample_rate


