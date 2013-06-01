"""
File iterators for determining over which files checkstyle should be run.
"""

from difflib import SequenceMatcher
from functools import partial
import os

from twitter.common.dirutil.fileset import Fileset


def path_iterator(args, options):
  for path in args:
    if os.path.isdir(path):
      for filename in Fileset.rglobs('*.py', root=path)():
        yield os.path.join(path, filename), None
    elif os.path.isfile(path):
      yield path, None


def diff_lines(blob_a, blob_b):
  matcher = SequenceMatcher(
      None, blob_a.data_stream.read().splitlines(1), blob_b.data_stream.read().splitlines(1))
  for opcode in matcher.get_opcodes():
    match_type, start_line_a, stop_line_a, start_line_b, stop_line_b = opcode
    if match_type == 'insert':
      for lineno in range(start_line_a, stop_line_a):
        yield lineno


def tuple_from_diff(diff):
  # New file => check all
  if diff.a_blob and not diff.b_blob and diff.a_blob.path.endswith('.py'):
    return diff.a_blob.path, None

  # Check diff lines between two
  if diff.a_blob and diff.b_blob and diff.a_blob.path.endswith('.py'):
    return diff.a_blob.path, partial(diff_lines, diff.a_blob, diff.b_blob)


def git_iterator(args, options):
  try:
    import git
  except ImportError:
    raise ValueError('Git has not been enabled for this checkstyle library!')

  branch = options.diff or 'master'
  repo = git.Repo()
  for filename, line_filter in filter(None, map(tuple_from_diff, repo.index.diff(branch))):
    yield filename, line_filter
