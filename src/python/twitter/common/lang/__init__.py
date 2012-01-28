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

class SingletonMetaclass(type):
  """
    Singleton metaclass.
  """
  def __init__(cls, name, bases, attrs):
    super(SingletonMetaclass, cls).__init__(name, bases, attrs)
    cls.instance = None

  def __call__(cls, *args, **kw):
    if cls.instance is None:
      cls.instance = super(SingletonMetaclass, cls).__call__(*args, **kw)
    return cls.instance

class Singleton(object):
  """
    Singleton mixin.
  """
  __metaclass__ = SingletonMetaclass
