# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

__author__ = 'Tejal Desai'

import logging
import os
import re
import subprocess
import tempfile
import unittest

from twitter.common.util.command_util import CommandUtil


class CommandUtilTest(unittest.TestCase):
  def test_execute_internal(self):
    temp_filename = tempfile.mktemp()
    handler = logging.FileHandler(temp_filename)
    logging.getLogger().addHandler(handler)
    logging.getLogger().setLevel(logging.INFO)
    ret = CommandUtil._execute_internal(['ls' , '-z' ], True, True, True)
    self.assertNotEqual(ret, 0)
    logging.getLogger().removeHandler(handler)
    with open(temp_filename, "r") as file1:
      str1 = file1.read()
    self.assertTrue(bool(re.search(".*Executing: ls -z.*", str1))) #command logged
    self.assertTrue(bool(re.search(".*illegal option.*", str1)) or bool(re.search(".*invalid option.*", str1))) #Error logged

  def test_execute(self):
    temp_filename = tempfile.mktemp()
    ret = CommandUtil.execute(['echo' , 'test'], True, temp_filename)
    self.assertEqual(ret, 0)
    with open(temp_filename, "r") as file1:
      str1 = file1.read()
    self.assertEqual("test\n", str1) #output stored in the input file
    os.remove(temp_filename)

  def test_execute_suppress_stdout(self):
    temp_filename = tempfile.mktemp()
    handler = logging.FileHandler(temp_filename)
    logging.getLogger().addHandler(handler)
    logging.getLogger().setLevel(logging.INFO)
    ret = CommandUtil.execute_suppress_stdout(['echo' , 'test'], True)
    self.assertEqual(ret, 0)
    with open(temp_filename, "r") as file1:
      str1 = file1.read()
    self.assertTrue(bool(re.search("Executing: echo", str1))) #command Logged
    self.assertFalse(bool(re.search("\ntest", str1)))   #Output not logged

  def test_execute_suppress_out_err(self):
    temp_filename = tempfile.mktemp()
    handler = logging.FileHandler(temp_filename)
    logging.getLogger().addHandler(handler)
    logging.getLogger().setLevel(logging.INFO)
    ret = CommandUtil.execute_suppress_stdout_stderr(['echo' , 'test'], True)
    self.assertEqual(ret, 0)
    with open(temp_filename, "r") as file1:
      str1 = file1.read()
    self.assertTrue(bool(re.search("Executing: echo test", str1)))  #command logged
    self.assertFalse(bool(re.search("\ntest", str1)))   #Output not logged

    #Test case 2
    ret = CommandUtil.execute_suppress_stdout_stderr(['ls' , '-z'], True)
    self.assertNotEqual(ret, 0)
    with open(temp_filename, "r") as file1:
      str1 = file1.read()

    self.assertTrue(bool(re.search("Executing: ls -z", str1)))  #command logged
    self.assertFalse(bool(re.search("illegal option", str1)))  #error not logged
    logging.getLogger().removeHandler(handler)

  def test_check_call(self):
    self.assertRaises(subprocess.CalledProcessError, CommandUtil.check_call, ['ls' , '-z'])
    ret = CommandUtil.check_call(['ls' , '-v'])
    self.assertEqual(ret, 0)

  def test_execute_and_get_output(self):
    (ret, output) = CommandUtil.execute_and_get_output(['echo', 'test'])
    self.assertEqual(ret, 0)
    self.assertEqual(output, 'test\n')

    (ret, output) = CommandUtil.execute_and_get_output(['echo1', 'test'])
    self.assertEqual(ret, 1)
    self.assertEqual(output, None)
