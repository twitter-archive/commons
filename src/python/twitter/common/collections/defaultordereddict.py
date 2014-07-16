# ==================================================================================================
# Copyright 2014 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from .ordereddict import OrderedDict


class DefaultOrderedDict(OrderedDict):
  """An OrderedDict that doesn't throw a KeyError when a key is missing
  """

  def __init__(self, *args, **kwds):
    super(DefaultOrderedDict, self).__init__(*args, **kwds)
    self.setmissing(lambda: None)

  def setmissing(self, missing):
    """Override the value to return when a key is missing
    :param missing:  a function that accepts a single arg (the key) and returns a default for
    a missing key
    """
    if not callable(missing):
      raise TypeError('expected missing argument to be a function')
    self._missing = missing

  def __missing__(self, key):
    if self._missing:
      value = self._missing()
      self.__setitem__(key, value)
      return value
    raise KeyError(key)

  @staticmethod
  def create(missing):
    """Construct a new OrderedDict with the specified missing key function
    :param missing: a function that accepts a single arg (the key) and returns a default for
    a missing key
    :return: a new instance of DefaultOrderedDict
    """
    newdict = DefaultOrderedDict()
    newdict.setmissing(missing)
    return newdict
