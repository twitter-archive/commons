import contextlib
import os
import random
from textwrap import dedent
import zipfile

from twitter.common.contextutil import temporary_dir
from twitter.common.dirutil import safe_mkdir, safe_mkdtemp
from twitter.common.lang import Compatibility
from twitter.common.python.distiller import Distiller
from twitter.common.python.installer import Installer
from twitter.common.python.util import DistributionHelper


if Compatibility.PY3:
  from contextlib import ExitStack

  @contextlib.contextmanager
  def nested(*context_managers):
    enters = []
    with ExitStack() as stack:
      for manager in context_managers:
        enters.append(stack.enter_context(manager))
      yield tuple(enters)

else:
  from contextlib import nested


def random_bytes(length):
  return ''.join(
      map(chr, (random.randint(ord('a'), ord('z')) for _ in range(length)))).encode('utf-8')


@contextlib.contextmanager
def temporary_content(content_map, interp=None, seed=31337):
  """Write content to disk where content is map from string => (int, string).

     If target is int, write int random bytes.  Otherwise write contents of string."""
  random.seed(seed)
  interp = interp or {}
  with temporary_dir() as td:
    for filename, size_or_content in content_map.items():
      safe_mkdir(os.path.dirname(os.path.join(td, filename)))
      with open(os.path.join(td, filename), 'wb') as fp:
        if isinstance(size_or_content, int):
          fp.write(random_bytes(size_or_content))
        else:
          fp.write((size_or_content % interp).encode('utf-8'))
    yield td


def yield_files(directory):
  for root, _, files in os.walk(directory):
    for f in files:
      filename = os.path.join(root, f)
      rel_filename = os.path.relpath(filename, directory)
      yield filename, rel_filename


def write_zipfile(directory, dest, reverse=False):
  with contextlib.closing(zipfile.ZipFile(dest, 'w')) as zf:
    for filename, rel_filename in sorted(yield_files(directory), reverse=reverse):
      zf.write(filename, arcname=rel_filename)
  return dest


PROJECT_CONTENT = {
  'setup.py': dedent('''
      from setuptools import setup

      setup(
          name='%(project_name)s',
          version='0.0.0',
          packages=['my_package'],
          package_data={'my_package': ['package_data/*.dat']},
      )
  '''),
  'my_package/__init__.py': 0,
  'my_package/my_module.py': '%(content)s',
  'my_package/package_data/resource1.dat': 1000,
  'my_package/package_data/resource2.dat': 1000,
}


@contextlib.contextmanager
def make_distribution(name='my_project', zipped=False, zip_safe=True):
  interp = {'project_name': name}
  if zip_safe:
    interp['content'] = dedent('''
    def do_something():
      print('hello world!')
    ''')
  else:
    interp['content'] = dedent('''
    if __file__ == 'derp.py':
      print('i am an idiot')
    ''')
  with temporary_content(PROJECT_CONTENT, interp=interp) as td:
    installer = Installer(td)
    distribution = installer.distribution()
    distiller = Distiller(distribution, debug=True)
    dist_location = distiller.distill(into=safe_mkdtemp())
    if zipped:
      yield DistributionHelper.distribution_from_path(dist_location)
    else:
      with temporary_dir() as td:
        extract_path = os.path.join(td, os.path.basename(dist_location))
        with contextlib.closing(zipfile.ZipFile(dist_location)) as zf:
          zf.extractall(extract_path)
        yield DistributionHelper.distribution_from_path(extract_path)
