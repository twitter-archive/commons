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

__author__ = 'tdesai'

import os
import subprocess
import sys

from twitter.common.contextutil import environment_as, temporary_file
from twitter.common.quantity import Amount, Data
from twitter.common.string import ScanfParser
from twitter.common.util.command_util import CommandUtil


class HDFSHelper(object):
  """
  This Class provides a set of functions for hdfs operations.
  NOTE: This class assumes a local hdfs or hadoop client on the path.
  """
  class InternalError(Exception): pass

  PARSER = ScanfParser('%(mode)s %(dirents)s %(user)s %(group)s %(filesize)d '
            '%(year)d-%(month)d-%(day)d %(hour)d:%(minute)d')

  def __init__(self, config, command_class=CommandUtil, heap_limit=Amount(256, Data.MB),
    use_hadoop_v1=False):
    """
    heap_limit is the maximum heap that should be allocated to the command process,
    defined using twitter.common.quantity.Data.

    use_hadoop_v1 sets the command to hadoop instead of hdfs.
    """
    if not os.path.isdir(config):
      raise ValueError('Command requires root of a config tree')
    self._config = config
    self._cmd_class = command_class
    if heap_limit is None:
      raise ValueError('The hdfs heap_limit must not be specified as "None".')
    self._heap_limit = heap_limit
    self.cli_command = 'hadoop' if use_hadoop_v1 else 'hdfs'
    if not self._cmd_class.cmd_within_path(self.cli_command):
      raise OSError('The "{0}" utility is not available on the system PATH'.format(
        self.cli_command))

  @property
  def config(self):
    return self._config

  def _call(self, cmd, *args, **kwargs):
    """Runs fs command with the given command and args.
    Checks the result of the call by default but this can be disabled with check=False.
    """
    cmd = [self.cli_command, '--config', self._config, 'dfs', cmd] + list(args)
    heapsize = str(int(self._heap_limit.as_(Data.MB)))
    with environment_as(HADOOP_HEAPSIZE=heapsize):
      if kwargs.get('check'):
        return self._cmd_class.check_call(cmd)
      elif kwargs.get('return_output'):
        return self._cmd_class.execute_and_get_output(cmd)
      elif kwargs.get('supress_output'):
        return self._cmd_class.execute_suppress_stdout(cmd)
      else:
        return self._cmd_class.execute(cmd)

  def get(self, src, dst):
    """
    Copy file(s) in HDFS to local path (via proxy if necessary).
    NOTE: If src matches multiple files, make sure dst is a directory!
    """
    if isinstance(src, list):
      hdfs_src = " ".join(src)
    else:
      hdfs_src = src
    return self._call('-get', hdfs_src, dst)

  def put(self, src, dst):
    """
    Copy the local file src to a HDFS path dst.
    """
    abs_src = os.path.expanduser(src)
    assert os.path.exists(abs_src), 'File does not exist, cannot copy: %s' % abs_src
    return self._do_put(abs_src, dst)

  def _do_put(self, source, dst):
    """
    Put the local file in to HDFS
    """
    if isinstance(dst, list):
      hdfs_dst = " ".join(dst)
    else:
      hdfs_dst = dst
    if not self._call('-test', '-e', hdfs_dst, check=False):
      self._call('-rm', '-skipTrash', hdfs_dst)
    return self._call('-put', source, hdfs_dst)

  def exists(self, path, flag='-e'):
    """
    Checks if the path exists in HDFS
    Returns true if it exists or else
    Returns false
    """
    try:
      return self._call("-test", flag, path) == 0
    except subprocess.CalledProcessError:
      return False

  def cat(self, remote_file_pattern, local_file=sys.stdout):
    """
    Cat HDFS file to local
    """
    return self._call("-cat", remote_file_pattern, also_output_to_file=local_file)

  def _ls(self, path, is_dir=False, is_recursive=False):
    """
    Return list of [hdfs_full_path, filesize]
    Raises exception when the HDFS ls command returns error
    """
    hdfs_cmd = '-lsr' if is_recursive else '-ls'
    (exit_code, ls_result) = self._call(hdfs_cmd, path, return_output=True)
    if exit_code != 0:
      raise self.InternalError("Error occurred. %s.Check logs for details" % ls_result)
    file_list = []
    if ls_result is None:
      return file_list
    lines = ls_result.splitlines()
    for line in lines:
      if line == "" or line.startswith("Found"):
        continue

      seg = line.split(None, 7)
      if len(seg) < 8:
        raise self.InternalError("Invalid hdfs -ls output. [%s]" % line)

      filename = seg[-1]
      try:
        metadata = self.PARSER.parse(' '.join(seg[0:7]))
      except ScanfParser.ParseError as e:
        raise self.InternalError('Unable to parse hdfs output: %s' % e)
      #seg[0] example: drwxrwx---
      if metadata.mode.startswith('d') != is_dir:
        continue

      file_list.append([filename, metadata.filesize])
    return file_list

  def ls(self, path, is_dir=False):
    """
    Returns list of [hdfs_full_path, filesize]
    If is_dir is true returns only the toplevel directories.
    """
    return self._ls(path, is_dir, False)

  def lsr(self, path, is_dir=False):
    """
    Returns list of [hdfs_full_path, filesize] in recursive manner
    If is_dir is true returns only the directories.
    """
    return self._ls(path, is_dir, True)

  def read(self, filename):
    """
      Return the contents of filename, or None if an error occurred.
    """
    with temporary_file() as fp:
      os.unlink(fp.name)
      if self._call("-copyToLocal", filename, fp.name) == 0:
        with open(fp.name) as f:
          return f.read()
      else:
        return None

  def write(self, filename, text):
    """
    Write will write the contents in the text to the filename given
    The file will be overwritten if it already exists
    """
    self._call("-rm", filename)
    with temporary_file() as fp:
      fp.write(text)
      fp.flush()
      return self._call('-copyFromLocal', fp.name, filename)

  def mkdir(self, path):
    """
    Mkdir will create a directory. If already present, it will return an error
    """
    return self._call("-mkdir", path)

  def mkdir_suppress_err(self, path):
    """
    Creates a directory if it does not exists
    """
    if not self.exists(path):
      return self.mkdir(path)

  def rm(self, filename):
    """
    Removes a file.
    """
    return self._call("-rm", filename, suppress_output=True)

  def cp(self, src, dest):
    """
    Copies a src file to dest
    """
    return self._call("-cp", src, dest, suppress_output=True)

  def mv(self, src, dest):
    """
    Move a src file to dest
    """
    return self._call("-mv", src, dest, suppress_output=True)

  def copy_from_local(self, local, remote):
    """
    Copies the file from local to remote
    """
    return self._call("-copyFromLocal", local, remote, suppress_output=True)

  def copy_to_local(self, remote, local):
    """
    Copies the file from remote to local
    """
    return self._call("-copyToLocal", remote, local, suppress_output=True)
