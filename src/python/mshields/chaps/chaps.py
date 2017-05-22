"""Chaps, a relative dir pants wrapper for Python targets."""
# pylint: disable=E0401

import os

from twitter.common import app, log

from sarge import capture_stdout, run, shell_format


log.LogOptions().disable_disk_logging()


def git_toplevel():
  """
  Grab absolute path of repo using git command.

  :returns: git.stdout.text.rstrip()
  :rtype: str.
  """
  git = capture_stdout("git rev-parse --show-toplevel")
  return git.stdout.text.rstrip()


def rel_cwd():
  """
  Given the cwd and git_toplevel result, constructs the relative path difference.

  :returns: os.path.relpath
  :rtype: str.
  """
  return os.path.relpath(os.getcwd(), git_toplevel())


def targets(path, args):
  """
  Assembles Fully Qualified Pants Targets (FQPT).

  :returns: space-delimited FQPT targets.
  :rtype: str.
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
  :param type: list `str`.
  """
  _targets = targets(rel_cwd(), args)
  log.debug("chaps targets: %s", _targets)

  pants_args = "binary {0}".format(_targets)
  pants(pants_args)


@app.command(name="list")
def list_goal():
  """List relative path pants targets."""
  path = rel_cwd()
  awk_args = shell_format("{0}", '/:/{print ":"$NF}')
  pants_args = "list {0} | awk -F: {1}".format(path, awk_args)

  pants(pants_args)


@app.command(name="repl")
def repl_goal(args):
  """
  Enter an ipython REPL.

  :param args: relative targets.
  :type args: list `str`.
  """
  _targets = targets(rel_cwd(), args)
  log.debug("chaps targets: %s", _targets)

  pants_args = "repl --repl-py-ipython {0}".format(_targets)
  pants(pants_args)


@app.command(name="run")
def run_goal(args):
  """
  Run a target using pants.

  :param args: relative targets.
  :param type: list `str`.
  """
  single_target = args[0]
  _targets = targets(rel_cwd(), [single_target])
  run_args = " ".join(args[1:])

  log.debug("chaps targets: %s", _targets)

  pants_args = "run {0} {1}".format(_targets, run_args)
  pants(pants_args)


@app.command_option("--coverage", dest="coverage", default=False, help="Python test coverage.")
@app.command(name="test")
def test_goal(args, options):
  """
  Use test.pytest goal with pants.

  :param args: relative targets.
  :param type: list `str`.
  :param options: twitter.common.app options.
  :param type: obj
  """
  _targets = targets(rel_cwd(), args)
  log.debug("chaps targets: %s", _targets)

  pants_args = "test.pytest  {0} {1}".format("--coverage=%d" % int(options.coverage), _targets)
  pants(pants_args)


app.add_option("--quiet", "-q", default=False)
app.main()
