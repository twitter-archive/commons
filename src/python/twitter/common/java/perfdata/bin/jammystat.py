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

import os
import glob

from twitter.common import app

from twitter.common.java.perfdata import PerfData


app.add_option(
    '-f',
    dest='filename',
    default=None,
    help='Filename to load hsperfdata from.')


app.add_option(
    '--hsperfdata_root',
    dest='hsperfdata_root',
    default='/tmp',
    help='Root directory to search for hsperfdata files.')


app.add_option(
    '-l',
    dest='list',
    default=False,
    action='store_true',
    help='List pids.')


app.add_option(
    '-p',
    dest='pid',
    default=None,
    type=int,
    help='PID to load hsperfdata from.')



def file_provider():
  options = app.get_options()
  def provider():
    with open(options.filename, 'rb') as fp:
      return fp.read()
  return provider


def list_pids():
  options = app.get_options()
  pattern = os.path.join(options.hsperfdata_root, 'hsperfdata_*', '*')
  for path in glob.glob(pattern):
    root, pid = os.path.split(path)
    dirname = os.path.basename(root)
    role = dirname[len('hsperfdata_'):]
    yield path, role, int(pid)


def print_pids():
  for path, role, pid in list_pids():
    print('role %s pid %d path %s' % (role, pid, path))


def pid_provider():
  options = app.get_options()
  for path, _, pid in list_pids():
    if pid == options.pid:
      break
  else:
    app.error('Could not find pid %s' % options.pid)
  def loader():
    with open(path, 'rb') as fp:
      return fp.read()
  return loader


def main(args, options):
  if len(args) > 0:
    app.error('Must provide hsperfdata via -f/-p')

  if options.list:
    print_pids()
    return

  perfdata = None
  if options.filename:
    perfdata = PerfData.get(file_provider())
  elif options.pid:
    perfdata = PerfData.get(pid_provider())

  if perfdata is None:
    app.error('No hsperfdata provider specified!')

  perfdata.sample()
  for key in sorted(perfdata):
    print('%s: %s' % (key, perfdata[key]))


app.main()
