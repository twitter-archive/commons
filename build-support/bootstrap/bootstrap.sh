#!/bin/sh

CWD=`dirname $0`
env python2.6 $CWD/bootstrap_pants.py
rc=$?

if [[ $rc != 0 ]]; then
  echo "Could not bootstrap virtualenv for Python!"
  exit $rc
fi
