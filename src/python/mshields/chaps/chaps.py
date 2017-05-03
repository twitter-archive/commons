"""Chaps, a relative dir pants wrapper."""
# pylint: disable=E0401

import os

from twitter.common import app, log

from sarge import capture_stdout, run


def git_toplevel():
  """
  Grab absolute path of repo using git command.

  :returns: git.stdout.text.rstrip()
  :rtype: str
  """
  git = capture_stdout("git rev-parse --show-toplevel")
  return git.stdout.text.rstrip()


def rel_cwd():
  """
  Given the cwd and git_toplevel result, constructs the relative path difference.

  :returns: os.path.relpath
  :rtype: str
  """
  return os.path.relpath(os.getcwd(), git_toplevel())


def targets(path, args):
  """
  Assembles Fully Qualified Pants Targets (FQPT).

  :returns: space-delimited FQPT targets.
  :rtype: str
  """
  return " ".join(["{0}{1}".format(path, target) for target in args])


def pants(args):
  """
  Grab the top level dir from git command, chdir and execute ./pants with given args.

  :param args: arguments to pass to subprocess.
  :type args: str
  :returns: _pants
  :rtype: sarge `obj`
  """
  os.chdir(git_toplevel())

  _pants = run("./pants %s" % args)

  return _pants


@app.command(name="binary")
def binary_goal(args):
  """
  Create a binary using pants.

  :param args: relative targets.
  :param rtype: list `str`
  """
  _targets = targets(rel_cwd(), args)
  log.debug("chaps targets: %s", _targets)

  pants_args = "binary {0}".format(_targets)
  pants(pants_args)


@app.command(name="list")
def list_goal():
  """List relative path pants targets."""
  path = rel_cwd()
  pants_args = "list {0}".format(path)
  pants(pants_args)


@app.command(name="repl")
def repl_goal(args):
  """
  Enter an ipython REPL.

  :param args: relative targets.
  :param rtype: list `str`
  """
  _targets = targets(rel_cwd(), args)
  log.debug("chaps targets: %s", _targets)

  pants_args = "repl --repl-py-ipython {0}".format(_targets)
  pants(pants_args)


app.add_option("--quiet", "-q", default=False)
app.main()
