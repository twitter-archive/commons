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

from __future__ import division, print_function

import os
import errno
import itertools
import posixpath
import subprocess

from contextlib import closing, contextmanager

from twitter.common import log
from twitter.common.contextutil import environment_as, temporary_file, temporary_dir
from twitter.common.dirutil import chmod_plus_x, safe_delete, safe_mkdir, safe_open, touch
from twitter.common.lang import Compatibility
from twitter.pants.goal.workunit import WorkUnit

if Compatibility.PY3:
  import urllib.request as urllib_request
  import urllib.error as urllib_error
else:
  import urllib2 as urllib_request
  import urllib2 as urllib_error

from twitter.pants.base import Config
from . import TaskError

_ID_BY_OS = {
  'linux': lambda release, machine: ('linux', machine),
  'darwin': lambda release, machine: ('darwin', release.split('.')[0]),
}


_PATH_BY_ID = {
  ('linux', 'x86_64'):  ['linux', 'x86_64'],
  ('linux', 'amd64'):   ['linux', 'x86_64'],
  ('linux', 'i386'):    ['linux', 'i386'],
  ('darwin', '9'):      ['mac', '10.5'],
  ('darwin', '10'):     ['mac', '10.6'],
  ('darwin', '11'):     ['mac', '10.7'],
  ('darwin', '12'):     ['mac', '10.8'],
}


def select_binary(base_path, version, name, config=None):
  """Selects a binary matching the current os and architecture.

  Raises TaskError if no binary of the given version and name could be found.
  """
  # TODO(John Sirois): finish doc of the path structure expexcted under base_path
  config = config or Config.load()
  cachedir = config.getdefault('pants_cachedir', default=os.path.expanduser('~/.pants.d'))
  baseurl = config.getdefault('pants_support_baseurl')
  timeout_secs = config.getdefault('pants_support_fetch_timeout_secs', type=int, default=30)

  sysname, _, release, _, machine = os.uname()
  os_id = _ID_BY_OS[sysname.lower()]
  if os_id:
    middle_path = _PATH_BY_ID[os_id(release, machine)]
    if middle_path:
      binary_path = os.path.join(base_path, *(middle_path + [version, name]))
      cached_binary_path = os.path.join(cachedir, binary_path)
      if not os.path.exists(cached_binary_path):
        url = posixpath.join(baseurl, binary_path)
        log.info('Fetching %s binary from: %s' % (name, url))
        downloadpath = cached_binary_path + '~'
        try:
          with closing(urllib_request.urlopen(url, timeout=timeout_secs)) as binary:
            with safe_open(downloadpath, 'wb') as cached_binary:
              cached_binary.write(binary.read())

          os.rename(downloadpath, cached_binary_path)
          chmod_plus_x(cached_binary_path)
        except (IOError, urllib_error.HTTPError, urllib_error.URLError) as e:
          raise TaskError('Failed to fetch binary from %s: %s' % (url, e))
        finally:
          safe_delete(downloadpath)
      log.debug('Selected %s binary cached at: %s' % (name, cached_binary_path))
      return cached_binary_path
  raise TaskError('No %s binary found for: %s' % (name, (sysname, release, machine)))


@contextmanager
def safe_args(args,
              max_args=None,
              config=None,
              argfile=None,
              delimiter='\n',
              quoter=None,
              delete=True):
  """
    Yields args if there are less than a limit otherwise writes args to an argfile and yields an
    argument list with one argument formed from the path of the argfile.

    :args The args to work with.
    :max_args The maximum number of args to let though without writing an argfile.  If not specified
              then the maximum will be loaded from config.
    :config Used to lookup the configured maximum number of args that can be passed to a subprocess;
            defaults to the default config and looks for key 'max_subprocess_args' in the DEFAULTS.
    :argfile The file to write args to when there are too many; defaults to a temporary file.
    :delimiter The delimiter to insert between args written to the argfile, defaults to '\n'
    :quoter A function that can take the argfile path and return a single argument value;
            defaults to:
            <code>lambda f: '@' + f<code>
    :delete If True deletes any arg files created upon exit from this context; defaults to True.
  """
  max_args = max_args or (config or Config.load()).getdefault('max_subprocess_args', int, 10)
  if len(args) > max_args:
    def create_argfile(fp):
      fp.write(delimiter.join(args))
      fp.close()
      return [quoter(fp.name) if quoter else '@%s' % fp.name]

    if argfile:
      try:
        with safe_open(argfile, 'w') as fp:
          yield create_argfile(fp)
      finally:
        if delete and os.path.exists(argfile):
          os.unlink(argfile)
    else:
      with temporary_file(cleanup=delete) as fp:
        yield create_argfile(fp)
  else:
    yield args


@contextmanager
def safe_classpath(logger=None):
  """
    Yields to a block in an environment with no CLASSPATH.  This is useful to ensure hermetic java
    invocations.
  """
  classpath = os.getenv('CLASSPATH')
  if classpath:
    logger = logger or log.warn
    logger('Scrubbing CLASSPATH=%s' % classpath)
  with environment_as(CLASSPATH=None):
    yield


class JvmCommandLine(object):
  def __init__(self, jvmargs=None, classpath=None, main=None, opts=None, args=None):
    object.__init__(self)

    tuplize = lambda x: tuple(x) if x else None

    self.jvmargs = tuplize(jvmargs)
    self.classpath = tuplize(classpath)
    self.main = main
    self.opts = tuplize(opts)
    self.args = tuplize(args)

  def __str__(self):
    cmd = self.callable_cmd()
    str = ' '.join(cmd)
    del cmd
    return str

  def call(self, indivisible=True, **kwargs):
    if indivisible:
      cmd_with_args = self.callable_cmd()
      with safe_classpath():
        returncode = _subprocess_call(cmd_with_args, **kwargs)
    else:
      cmd = self.callable_cmd(use_args=False)
      with safe_classpath():
        returncode = _subprocess_call_with_args(cmd, self.args, **kwargs)
    return returncode

  def callable_cmd(self, use_args=True):
    """Returns a list ready to be used by subprocess.call() or subprocess.Popen()
    opts= can be either a list of strings or (better) a list of tuples
    args= is a list of paths"""

    cmd = ['java']
    if self.jvmargs:
      cmd.extend(self.jvmargs)
    if self.classpath:
      cmd.extend(('-cp' if self.main else '-jar', os.pathsep.join(self.classpath)))
    if self.main:
      cmd.append(self.main)
    if self.opts:
      # opts can be [ opt1, val1, opt2, val2, opt3, opt4 ]
      # or
      # opts can be [(opt1, val1), (opt2, val2), (opt3,), (opt4,)]
      # the latter is prefered because it adds more useful structure
      # itertools.chain flattens the latter into the former
      cmd.extend(itertools.chain(*self.opts))
    if self.args and use_args:
      cmd.extend(self.args)
    return cmd


def _runjava_cmd(jvmargs=None, classpath=None, main=None, opts=None, args=None):
  cmd = ['java']
  if jvmargs:
    cmd.extend(jvmargs)
  if classpath:
    cmd.extend(('-cp' if main else '-jar', os.pathsep.join(classpath)))
  if main:
    cmd.append(main)
  if opts:
    cmd.extend(opts)
  if args:
    cmd.extend(args)
  return cmd


def _runjava_cmd_to_str(cmd):
  return ' '.join(cmd)


def runjava_cmd_str(jvmargs=None, classpath=None, main=None, opts=None, args=None):
  cmd = _runjava_cmd(jvmargs=jvmargs, classpath=classpath, main=main, opts=opts, args=args)
  return _runjava_cmd_to_str(cmd)


def runjava_indivisible(jvmargs=None, classpath=None, main=None, opts=None, args=None, dryrun=False,
                        workunit_factory=None, workunit_name=None, **kwargs):
  """Spawns a java process with the supplied configuration and returns its exit code.
  The args list is indivisible so it can't be split across multiple invocations of the command
  similiar to xargs.
  Passes kwargs through to subproccess.call.
  """
  cmd_with_args = _runjava_cmd(jvmargs=jvmargs, classpath=classpath, main=main, opts=opts,
                               args=args)
  if dryrun:
    return _runjava_cmd_to_str(cmd_with_args)
  else:
    with safe_classpath():
      return _subprocess_call(cmd_with_args, workunit_factory=workunit_factory,
                              workunit_name=workunit_name or main, **kwargs)


def runjava(jvmargs=None, classpath=None, main=None, opts=None, args=None, dryrun=False,
            workunit_factory=None, workunit_name=None, **kwargs):
  """Spawns a java process with the supplied configuration and returns its exit code.
  The args list is divisable so it can be split across multiple invocations of the command
  similiar to xargs.
  Passes kwargs through to subproccess.call.
  """
  cmd = _runjava_cmd(jvmargs=jvmargs, classpath=classpath, main=main, opts=opts)
  if dryrun:
    return _runjava_cmd_to_str(cmd)
  else:
    with safe_classpath():
      return _subprocess_call_with_args(cmd, args, workunit_factory=workunit_factory,
                                        workunit_name=workunit_name or main, **kwargs)


def _split_args(i):
  l = list(i)
  half = len(l)//2
  return l[:half], l[half:]


def _subprocess_call(cmd_with_args, call=subprocess.call, workunit_factory=None,
                     workunit_name=None, **kwargs):
  cmd_str = ' '.join(cmd_with_args)
  log.debug('Executing: %s' % cmd_str)
  if workunit_factory:
    workunit_labels = [WorkUnit.TOOL, WorkUnit.JVM]
    with workunit_factory(name=workunit_name, labels=workunit_labels, cmd=cmd_str) as workunit:
      try:
        ret = call(cmd_with_args, stdout=workunit.output('stdout'),
                   stderr=workunit.output('stderr'), **kwargs)
        workunit.set_outcome(WorkUnit.FAILURE if ret else WorkUnit.SUCCESS)
        return ret
      except OSError as e:
        if errno.E2BIG == e.errno:
          # _subprocess_call_with_args will split and retry,
          # so we want this to appear to have succeeded.
          workunit.set_outcome(WorkUnit.SUCCESS)
  else:
    return call(cmd_with_args, **kwargs)


def _subprocess_call_with_args(cmd, args, call=subprocess.call,
                               workunit_factory=None, workunit_name=None, **kwargs):
  cmd_with_args = cmd[:]
  if args:
    cmd_with_args.extend(args)
  try:
    with safe_classpath():
      return _subprocess_call(cmd_with_args, call=call, workunit_factory=workunit_factory,
                              workunit_name=workunit_name, **kwargs)
  except OSError as e:
    if errno.E2BIG == e.errno and args and len(args) > 1:
      args1, args2 = _split_args(args)
      result1 = _subprocess_call_with_args(cmd, args1, call=call, **kwargs)
      result2 = _subprocess_call_with_args(cmd, args2, call=call, **kwargs)
      # we are making one command into two so if either fails we return fail
      result = 0
      if 0 != result1 or 0 != result2:
        result = 1
      return result
    else:
      raise e

def profile_classpath(profile, java_runner=None, config=None, ivy_jar=None, ivy_settings=None,
                      workunit_factory=None):
  # TODO(John Sirois): consider rework when ant backend is gone and there is no more need to share
  # path structure

  java_runner = java_runner or runjava_indivisible

  config = config or Config.load()

  profile_dir = config.get('ivy-profiles', 'workdir')
  profile_libdir = os.path.join(profile_dir, '%s.libs' % profile)
  profile_check = '%s.checked' % profile_libdir
  if not os.path.exists(profile_check):
    # TODO(John Sirois): refactor IvyResolve to share ivy invocation command line bits
    ivy_classpath = [ivy_jar] if ivy_jar else config.getlist('ivy', 'classpath')

    safe_mkdir(profile_libdir)
    ivy_settings = ivy_settings or config.get('ivy', 'ivy_settings')
    ivy_xml = os.path.join(profile_dir, '%s.ivy.xml' % profile)
    ivy_opts = [
      '-settings', ivy_settings,
      '-ivy', ivy_xml,

      # TODO(John Sirois): this pattern omits an [organisation]- prefix to satisfy IDEA jar naming
      # needs for scala - isolate this hack to idea.py where it belongs
      '-retrieve', '%s/[artifact]-[revision](-[classifier]).[ext]' % profile_libdir,

      '-sync',
      '-symlink',
      '-types', 'jar', 'bundle',
      '-confs', 'default'
    ]
    result = java_runner(classpath=ivy_classpath, main='org.apache.ivy.Main',
                         workunit_factory=workunit_factory,
                         workunit_name='%s:bootstrap' % profile, opts=ivy_opts)
    if result != 0:
      raise TaskError('Failed to load profile %s, ivy exit code %s' % (profile, str(result)))
    touch(profile_check)

  return [os.path.join(profile_libdir, jar) for jar in os.listdir(profile_libdir)]


def _mac_open(files):
  subprocess.call(['open'] + list(files))


def _linux_open(files):
  for f in list(files):
    subprocess.call(['xdg-open', f])


_OPENER_BY_OS = {
  'darwin': _mac_open,
  'linux': _linux_open
}


def ui_open(*files):
  """Attempts to open the given files using the preferred native viewer or editor."""
  if files:
    osname = os.uname()[0].lower()
    if not osname in _OPENER_BY_OS:
      print('Sorry, open currently not supported for ' + osname)
    else:
      _OPENER_BY_OS[osname](files)


def find_java_home():
  # A kind-of-insane hack to find the effective java home. On some platforms there are so
  # many hard and symbolic links into the JRE dirs that it's actually quite hard to
  # establish what path to use as the java home, e.g., for the purpose of rebasing.
  # In practice, this seems to work fine.
  #
  # TODO: In the future we should probably hermeticize the Java enivronment rather than relying
  # on whatever's on the shell's PATH. E.g., you either specify a path to the Java home via a
  # cmd-line flag or .pantsrc, or we infer one with this method but verify that it's of a
  # supported version.
  with temporary_dir() as tmpdir:
    with open(os.path.join(tmpdir, 'X.java'), 'w') as srcfile:
      srcfile.write('''
        class X {
          public static void main(String[] argv) {
            System.out.println(System.getProperty("java.home"));
          }
        }''')
    subprocess.Popen(['javac', '-d', tmpdir, srcfile.name],
                     stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()
    return subprocess.Popen(['java', '-cp', tmpdir, 'X'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()[0]
