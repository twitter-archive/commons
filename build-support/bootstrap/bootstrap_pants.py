#!/usr/bin/env python2.6

import os
import sys
import subprocess
import glob
import shutil
import tempfile

cwd = os.path.dirname(os.path.abspath(sys.argv[0]))
print 'CWD is %s' % cwd

# detect BUILD_ROOT
build_root = os.path.abspath(os.path.dirname(sys.argv[0]))
while not os.path.exists(os.path.join(build_root, '.git')):
  build_root = os.path.dirname(build_root)
if not os.path.exists(os.path.join(build_root, '.git')):
  print >> sys.stderr, 'Could not bootstrap sane environment.'
  sys.exit(1)

print 'BUILD_ROOT detected as %s' % build_root

# set new USER_SITE
virtualenv = os.path.join(build_root, '.python')
print 'Installing virtualenv in %s' % virtualenv

# perform the build work in a temp dir
tempdir = tempfile.mkdtemp(prefix = '.python.bootstrap')
os.chdir(tempdir)

virtualenv_install_args = [
  sys.executable,
  os.path.join(cwd, 'virtualenv.py'),
  "--no-site-packages",
  "--distribute",
  "--prompt=pants>> ",
  "-v",
  virtualenv
]

po = subprocess.Popen(virtualenv_install_args)
rv = po.wait()
if rv != 0:
  print >> sys.stderr, 'Eek, looks like we failed to install!'
  sys.exit(1)

os.chdir(cwd)
print 'Cleaning up staging directory: %s' % tempdir
shutil.rmtree(tempdir)

sys.exit(0)
