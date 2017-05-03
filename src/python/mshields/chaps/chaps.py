"""Jeans, a relative dir pants wrapper."""

import os

from twitter.common import app, log

from sarge import Capture, capture_stdout, run


def git_toplevel():
  git = capture_stdout('git rev-parse --show-toplevel')
  return git.stdout.text


def pants(args):
  """
  Grab the top level dir from git command, chdir and execute ./pants with given args.

  :param args: arguments to pass to subprocess.
  :type args: str
  :returns: _pants
  :rtype: sarge `obj`
  """
  git = capture_stdout('git rev-parse --show-toplevel')
  os.chdir(git.stdout.text.rstrip())
  _pants = run('./pants %s' % args, stdout=Capture())
  return _pants


@app.command
def binary(args, options):
  rel_cwd = os.path.relpath(os.getcwd(), git_toplevel())

  targets = ' '.join(['{0}{1}'.format(rel_cwd, target) for target in args])
  log.debug('jeans targets: %s', targets)

  pants_args = 'binary {0}'.format(targets)
  log.debug(pants(pants_args))


app.main()
