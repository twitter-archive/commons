import contextlib
import os

from zipfile import ZipFile

from twitter.common.contextutil import temporary_dir
from twitter.common.python.http import Web
from twitter.common.python.package import EggPackage, SourcePackage
from twitter.common.python.testing import create_layout

from pkg_resources import Requirement, parse_version
import pytest


def test_source_packages():
  for ext in ('.tar.gz', '.tar', '.tgz', '.zip', '.tar.bz2'):
    sl = SourcePackage('a_p_r-3.1.3' + ext)
    assert sl._name == 'a_p_r'
    assert sl.name == 'a-p-r'
    assert sl.raw_version == '3.1.3'
    assert sl.version == parse_version(sl.raw_version)
    for req in ('a_p_r', 'a_p_r>2', 'a_p_r>3', 'a_p_r>=3.1.3', 'a_p_r==3.1.3', 'a_p_r>3,<3.5'):
      assert sl.satisfies(req)
      assert sl.satisfies(Requirement.parse(req))
    for req in ('foo', 'a_p_r==4.0.0', 'a_p_r>4.0.0', 'a_p_r>3.0.0,<3.0.3', 'a==3.1.3'):
      assert not sl.satisfies(req)
  sl = SourcePackage('python-dateutil-1.5.tar.gz')
  assert sl.name == 'python-dateutil'
  assert sl.raw_version == '1.5'

  with temporary_dir() as td:
    dateutil_base = 'python-dateutil-1.5'
    dateutil = '%s.zip' % dateutil_base
    with contextlib.closing(ZipFile(os.path.join(td, dateutil), 'w')) as zf:
      zf.writestr(os.path.join(dateutil_base, 'file1.txt'), 'junk1')
      zf.writestr(os.path.join(dateutil_base, 'file2.txt'), 'junk2')
    sl = SourcePackage('file://' + os.path.join(td, dateutil), opener=Web())
    with temporary_dir() as td2:
      sl.fetch(location=td2)
      print(os.listdir(td2))
      assert set(os.listdir(os.path.join(td2, dateutil_base))) == set(['file1.txt', 'file2.txt'])


def test_egg_packages():
  el = EggPackage('psutil-0.4.1-py2.6-macosx-10.7-intel.egg')
  assert el.name == 'psutil'
  assert el.raw_version == '0.4.1'
  assert el.py_version == '2.6'
  assert el.platform == 'macosx-10.7-intel'
  for req in ('psutil', 'psutil>0.4', 'psutil==0.4.1', 'psutil>0.4.0,<0.4.2'):
    assert el.satisfies(req)
  for req in ('foo', 'bar==0.4.1'):
    assert not el.satisfies(req)

  el = EggPackage('pytz-2012b-py2.6.egg')
  assert el.name == 'pytz'
  assert el.raw_version == '2012b'
  assert el.py_version == '2.6'
  assert el.platform is None

  # Eggs must have their own version and a python version.
  with pytest.raises(EggPackage.InvalidLink):
    EggPackage('bar.egg')

  with pytest.raises(EggPackage.InvalidLink):
    EggPackage('bar-1.egg')

  with pytest.raises(EggPackage.InvalidLink):
    EggPackage('bar-py2.6.egg')

  dateutil = 'python_dateutil-1.5-py2.6.egg'
  with create_layout([dateutil]) as td:
    el = EggPackage('file://' + os.path.join(td, dateutil), opener=Web())

    with temporary_dir() as td2:
      # local file fetch w/o location will always remain same
      loc1 = el.fetch()
      assert loc1 == os.path.join(td, dateutil)

      el.fetch(location=td2)
      assert os.listdir(td2) == [dateutil]
