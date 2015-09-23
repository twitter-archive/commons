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

import sys
import unittest
from mock import patch

import fake_filesystem as pyfakefs

import twitter.common.fs
from twitter.common.fs import HDFSHelper


class MockCommandUtil:

  @staticmethod
  def execute(cmd, get_output=True):
    if (cmd[4] == '-lsr' or cmd[4] == '-ls') and cmd[5] =='path':
      return (0,"\n".join(["Found 1 items",
            "drwxr-xr-x   - tdesai staff         68 2012-08-06 13:51 hadoop_dir/test_dir",
            "-rwxrwxrwx   1 tdesai staff          6 2012-08-06 14:01 tejal.txt",
            "-rwxrwxrwx   1 tdesai staff          6 2012-08-06 14:01 tejal txt"]))

    if (cmd[4] == '-lsr' or cmd[4] == '-ls') and cmd[5] =='non_existing':
      return (255,"ls: File doesnot exists")

    if (cmd[4] == '-lsr' or cmd[4] == '-ls') and cmd[5] =='empty':
      return (0,None)

    if cmd[4] == '-test':
      return " ".join(cmd) == \
          'hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -test -e hadoop_dir'

    if cmd[4] == '-copyToLocal':
      if get_output:
        tmp_file = cmd[6]
        if " ".join(cmd) == \
            "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -copyToLocal " + \
             "somefile " + tmp_file:
          with open(tmp_file, "w") as f:
            f.write("read_test")
          return 0
        elif " ".join(cmd) == \
            "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -copyToLocal " + \
             "non_exist " + tmp_file:
          return 1

    if cmd[4] == '-copyFromLocal':
      if cmd[5] != 'text_file':
        tmp_file = cmd[5]
        with open(tmp_file, "r") as f:
          text1 = f.read()
        return (text1 == "write_text" and
          " ".join(cmd) == " ".join(["hadoop", "--config", "/etc/hadoop/hadoop-conf-tst-smf1",
                                     "dfs", "-copyFromLocal", tmp_file, "somefile"]))
    #For rest all cases return the command
    return " ".join(cmd)

  @staticmethod
  def execute_and_get_output(cmd):
    return MockCommandUtil.execute(cmd, True)

  @staticmethod
  def execute_suppress_stdout(cmd):
    return MockCommandUtil.execute(cmd, get_output=False)

class HdfsTest(unittest.TestCase):
  _config_dir = "/etc/hadoop/hadoop-conf-tst-smf1"
  _site_config = "%s/site.xml" % _config_dir
  _original_cwd = None

  def setUp(self):
    fake_fs = pyfakefs.FakeFilesystem()
    fake_os = pyfakefs.FakeOsModule(fake_fs)
    fake_fs.CreateFile(HdfsTest._site_config, contents="this is not a real file.")
    fake_fs.CreateFile("src", contents="heh. before pyfakefs this was unintentionally a dir.")

    self.original_os = twitter.common.fs.hdfs.os
    twitter.common.fs.hdfs.os = fake_os

  def tearDown(self):
    twitter.common.fs.hdfs.os = self.original_os

  def test_get_config_behavior(self):
    self.assertRaises(ValueError, HDFSHelper, "/this/does/not/exist",
                      command_class=MockCommandUtil)
    self.assertRaises(ValueError, HDFSHelper, HdfsTest._site_config,
                      command_class=MockCommandUtil)

  def test_get_config(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    self.assertEqual(hdfs_helper.config,'/etc/hadoop/hadoop-conf-tst-smf1')

  def test_get(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.get(['src'],"dst")
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -get src dst"
    self.assertEqual(cmd, expected_cmd)

  def test_put(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.put('src','dst')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -put src dst"
    self.assertEqual(cmd, expected_cmd)

  def test_cat(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.cat('text_file', 'local')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -cat " + \
      "text_file"
    self.assertEqual(cmd, expected_cmd)

  def test_hdfs_ls(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.ls('path', True)
    expected_output_dir = [['hadoop_dir/test_dir', 68]]
    expected_output = [['tejal.txt', 6], ['tejal txt',6]]
    self.assertEqual(cmd, expected_output_dir)
    cmd = hdfs_helper.ls('path')
    self.assertEqual(cmd, expected_output)
    #Empty path
    cmd = hdfs_helper.ls('empty', True)
    self.assertTrue(not cmd)
    #Return code 255
    self.assertRaises(HDFSHelper.InternalError,hdfs_helper.ls,'non_existing', True )


  def test_hdfs_lsr(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    expected_output = [['tejal.txt', 6], ['tejal txt',6]]
    expected_output_dir = [['hadoop_dir/test_dir', 68]]
    cmd = hdfs_helper.lsr('path')
    self.assertEqual(cmd, expected_output)
    cmd = hdfs_helper.lsr('path', True)
    self.assertEqual(cmd, expected_output_dir)

  def test_exists(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    self.assertEquals(0, hdfs_helper.exists('hadoop_dir'))

  def test_read(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    read_text = hdfs_helper.read('somefile')
    self.assertEqual("read_test", read_text)
    read_text = hdfs_helper.read('non_exist')
    self.assertEqual(None, read_text)

  def test_write(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    self.assertEqual(True, hdfs_helper.write('somefile',"write_text"))

  def test_mkdir(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.mkdir('dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -mkdir dest"
    self.assertEqual(cmd, expected_cmd)

  def test_rm(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.rm('dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -rm dest"
    self.assertEqual(cmd, expected_cmd)

  def test_cp(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.cp('src','dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -cp src dest"
    self.assertEqual(cmd, expected_cmd)

  def test_mv(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.mv('src', 'dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs -mv src dest"
    self.assertEqual(cmd, expected_cmd)

  def test_copy_from_local(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.copy_from_local('text_file','dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs " + \
      "-copyFromLocal text_file dest"
    self.assertEqual(cmd, expected_cmd)

  def test_copy_to_local(self):
    hdfs_helper = HDFSHelper("/etc/hadoop/hadoop-conf-tst-smf1",
                             command_class=MockCommandUtil)
    cmd = hdfs_helper.copy_to_local('text_file','dest')
    expected_cmd = "hadoop --config /etc/hadoop/hadoop-conf-tst-smf1 dfs " + \
      "-copyToLocal text_file dest"
    self.assertEqual(cmd, expected_cmd)
