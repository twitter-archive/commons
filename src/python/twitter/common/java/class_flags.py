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

from .java_types import *

class ClassFlags(object):
  """http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#75734
  """
  ACC_PUBLIC	 = 0x0001
  ACC_FINAL	 = 0x0010
  ACC_SUPER	 = 0x0020
  ACC_INTERFACE	 = 0x0200
  ACC_ABSTRACT	 = 0x0400

  def __init__(self, flags):
    self._flags = flags

  def public(self):
    return self._flags & ClassFlags.ACC_PUBLIC

  def final(self):
    return self._flags & ClassFlags.ACC_FINAL

  def super_(self):
    return self._flags & ClassFlags.ACC_SUPER

  def interface(self):
    return self._flags & ClassFlags.ACC_INTERFACE

  def abstract(self):
    return self._flags & ClassFlags.ACC_ABSTRACT

  def __str__(self):
    verbs = []
    if self.public(): verbs.append('public')
    if self.final(): verbs.append('final')
    if self.super_(): verbs.append('super')
    if self.interface(): verbs.append('interface')
    if self.abstract(): verbs.append('abstract')
    return ' '.join(verbs)
