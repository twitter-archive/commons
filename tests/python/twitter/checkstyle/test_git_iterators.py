import os

from twitter.checkstyle.iterators import git_iterator

import pytest
import git


non_python_filename = "non-python-file"
python_filename = "python-file.py"
other_branch = 'other_branch'
feature_branch = 'feature_branch'


class Options(object):
  def __init__(self, diff=None):
    self.diff = diff


@pytest.fixture
def repo(tmpdir):
  os.chdir(tmpdir.strpath)
  repo = git.Repo.init(tmpdir.strpath)

  tmpdir.join(non_python_filename).write("content")
  tmpdir.join(python_filename).write("content")

  repo.index.add([python_filename, non_python_filename])
  repo.index.commit('initial commit')

  # Create two branch and set head to feature_branch
  repo.create_head(other_branch)
  repo.create_head(feature_branch)
  repo.head.reference = repo.create_head(feature_branch)

  return repo


def test_no_py_file_changed(tmpdir, repo):
  tmpdir.join(non_python_filename).write('some more')
  repo.index.add([non_python_filename])
  repo.index.commit("modify a non-python file")

  assert [f[0] for f in git_iterator(None, Options())] == []


def test_py_changed_in_branch(tmpdir, repo):
  # Create a commit in other branch and diff against it
  repo.heads.other_branch.checkout()
  tmpdir.join(python_filename).write('some more')
  repo.index.add([python_filename])
  repo.index.commit("modify a python file in another branch")
  repo.heads.feature_branch.checkout()

  assert [f[0] for f in git_iterator(None, Options(other_branch))] == [
      tmpdir.join(python_filename).strpath]


def test_py_file_changed(tmpdir, repo):
  # First create a commit on master. Since we diff against the merge-base to master
  # by default, it shouldn't be in the diff.
  repo.heads.master.checkout()
  python_filename_on_master = 'some-python-file.py'
  tmpdir.join('some-python-file.py').write('some python in master')
  repo.index.add([python_filename_on_master])
  repo.index.commit("commit on master")

  repo.heads.feature_branch.checkout()
  tmpdir.join(python_filename).write('some python to check')
  repo.index.add([python_filename])
  repo.index.commit("modify a python file")

  assert [f[0] for f in git_iterator(None, Options())] == [tmpdir.join(python_filename).strpath]


def test_py_file_added(tmpdir, repo):
  repo.head.reference.commit
  new_python_filename = "newfile.py"
  tmpdir.join(new_python_filename).write('some python to check')
  repo.index.add([new_python_filename])
  repo.index.commit("add a python file")

  assert [f[0] for f in git_iterator(None, Options())] == [tmpdir.join(new_python_filename).strpath]


def test_py_file_renamed(tmpdir, repo):
  new_python_filename = "newfile.py"
  tmpdir.join(new_python_filename).write(tmpdir.join(python_filename).read())
  repo.index.remove([python_filename])
  repo.index.add([new_python_filename])
  repo.index.commit("rename a python file")

  assert [f[0] for f in git_iterator(None, Options())] == [tmpdir.join(new_python_filename).strpath]
