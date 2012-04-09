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

__author__ = 'Brian Wickman'

from numbers import Integral
from twitter.common.lang import total_ordering

class AmountUnit(object):
  def __init__(self, multiplier, base_or_unit, display):
    if isinstance(base_or_unit, Integral):
      self._multiplier = multiplier * int(base_or_unit)
    elif isinstance(base_or_unit, AmountUnit):
      self._multiplier = multiplier * int(base_or_unit.multiplier())
    else:
      raise TypeError('Unknown type for base_or_unit: %s' % type(base_or_unit))
    self._display = display

  def multiplier(self):
    return self._multiplier

  def __str__(self):
    return self._display


class Time(AmountUnit):
  def __init__(self, multiplier, base, display):
    AmountUnit.__init__(self, multiplier, base, display)


Time.NANOSECONDS   = Time(   1,                 1, "ns")
Time.MICROSECONDS  = Time(1000,  Time.NANOSECONDS, "us")
Time.MILLISECONDS  = Time(1000, Time.MICROSECONDS, "ms")
Time.SECONDS       = Time(1000, Time.MILLISECONDS, "secs")
Time.MINUTES       = Time(  60,      Time.SECONDS, "mins")
Time.HOURS         = Time(  60,      Time.MINUTES, "hrs")
Time.DAYS          = Time(  24,        Time.HOURS, "days")
Time.BASES = [
  Time.NANOSECONDS,
  Time.MICROSECONDS,
  Time.MILLISECONDS,
  Time.SECONDS,
  Time.MINUTES,
  Time.HOURS,
  Time.DAYS
]


class Data(AmountUnit):
  def __init__(self, multiplier, base, display):
    AmountUnit.__init__(self, multiplier, base, display)

Data.BYTES = Data(   1,          1, "B")
Data.KB    = Data(1024, Data.BYTES, "KB")
Data.MB    = Data(1024,    Data.KB, "MB")
Data.GB    = Data(1024,    Data.MB, "GB")
Data.TB    = Data(1024,    Data.GB, "TB")
Data.PB    = Data(1024,    Data.TB, "PB")

Data.BASES = [
  Data.BYTES,
  Data.KB,
  Data.MB,
  Data.GB,
  Data.TB,
  Data.PB
]


@total_ordering
class Amount(object):
  def __init__(self, amount, unit):
    if not isinstance(amount, Integral):
      raise ValueError('Amount should be an integer type.')
    if not isinstance(unit, AmountUnit):
      raise TypeError('unit should be of type AmountUnit.')
    self._amount = amount
    self._unit = unit
    self._reduce()

  def _reduce(self):
    if not hasattr(self._unit, 'BASES'):
      return
    index = self._unit.BASES.index(self._unit)

    amount = self._amount
    unit = self._unit
    for base in self._unit.BASES[index+1:]:
      new_amount = (1. * self._amount * self._unit.multiplier()) / base.multiplier()
      if new_amount.is_integer():
        amount = int(new_amount)
        unit = base

    self._amount = amount
    self._unit = unit

  def amount(self):
    return self._amount

  def unit(self):
    return self._unit

  def _calc(self):
    return self._amount * self._unit.multiplier()

  def _raise_if_incompatible(self, other):
    if type(self._unit) != type(other._unit):
      raise TypeError('Cannot convert from disparate base units: %s vs %s' % (
        type(self._unit), type(other._unit)))

  def __eq__(self, other):
    self._raise_if_incompatible(other)
    return self._calc() == other._calc()

  def __lt__(self, other):
    self._raise_if_incompatible(other)
    return self._calc() < other._calc()

  def __add__(self, other):
    self._raise_if_incompatible(other)
    return Amount(self._calc() + other._calc(), self._unit.BASES[0])

  def __sub__(self, other):
    self._raise_if_incompatible(other)
    return Amount(self._calc() - other._calc(), self._unit.BASES[0])

  def __mul__(self, other):
    if not isinstance(other, Integral):
      raise TypeError('Can only multiply by integers')
    return Amount(self._calc() * other, self._unit.BASES[0])

  def __rmul__(self, other):
    return self.__mul__(other)

  def as_(self, unit):
    if type(self._unit) != type(unit):
      raise TypeError('Cannot convert from disparate base units: %s vs %s' % (
        type(self._unit), type(unit)))
    return self._amount * 1.0 * self._unit.multiplier() / unit.multiplier()

  def __str__(self):
    return '%s %s' % (self._amount, self._unit)

  def __repr__(self):
    return 'Amount(%s, %s)' % (self._amount, self._unit)

__all__ = [
  'Time',
  'Data',
  'Amount'
]
