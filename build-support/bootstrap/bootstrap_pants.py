import os
import sys
import subprocess
import glob
import shutil
import tempfile

cwd = os.path.dirname(os.path.abspath(sys.argv[0]))

# detect BUILD_ROOT
build_root = os.path.abspath(os.path.dirname(sys.argv[0]))
while not os.path.exists(os.path.join(build_root, '.git')):
  build_root = os.path.dirname(build_root)
if not os.path.exists(os.path.join(build_root, '.git')):
  sys.stderr.write('Could not bootstrap sane environment.\n')
  sys.exit(1)

sys.stdout.write('BUILD_ROOT detected as %s\n' % build_root)

# set new USER_SITE
virtualenv = os.path.join(build_root, '.python', 'bootstrap')
sys.stdout.write('Installing virtualenv in %s\n' % virtualenv)

# perform the build work in a temp dir
tempdir = tempfile.mkdtemp(prefix = '.python.bootstrap')
os.chdir(tempdir)

virtualenv_install_args = [
  sys.executable,
  os.path.join(cwd, 'virtualenv.py'),
  "--no-site-packages",
  "--distribute",
  "-v",
  virtualenv
]

po = subprocess.Popen(virtualenv_install_args)
rv = po.wait()
if rv != 0:
  sys.stderr.write('Eek, looks like we failed to install!\n')
  sys.exit(1)

os.chdir(cwd)
sys.stdout.write('Cleaning up staging directory: %s\n' % tempdir)
shutil.rmtree(tempdir)
sys.exit(0)
