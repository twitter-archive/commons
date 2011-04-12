# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

__author__ = 'John Sirios'

from pants import (
  CycleException,
  InternalTarget,
)

import unittest

class MockTarget(object):
  def __init__(self, address, *internal_dependencies):
    self.address = address
    self.internal_dependencies = internal_dependencies

  def __repr__(self):
    return self.address

class InternalTargetTest(unittest.TestCase):
  def testDetectCycleDirect(self):
    a = MockTarget('a')

    # no cycles yet
    InternalTarget.check_cycles(a)
    a.internal_dependencies = [ a ]
    try:
      InternalTarget.check_cycles(a)
      self.fail("Expected a cycle to be detected")
    except CycleException:
      # expected
      pass

  def testDetectIndirect(self):
    c = MockTarget('c')
    b = MockTarget('b', c)
    a = MockTarget('a', c, b)

    # no cycles yet
    InternalTarget.check_cycles(a)

    c.internal_dependencies = [ a ]
    try:
      InternalTarget.check_cycles(a)
      self.fail("Expected a cycle to be detected")
    except CycleException:
      # expected
      pass
