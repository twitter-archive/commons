#!/usr/bin/env bash

function banner() {
  echo
  echo "[== $@ ==]"
  echo
}

function die() {
  if (( $# > 0 )); then
    echo -e "\n$@"
  fi
  exit 1
}

function usage() {
  echo "Runs commons tests for local or hosted CI."
  echo
  echo "Usage: $0 (-h|-bsdjp)"
  echo " -h           print out this help message"
  echo " -b           skip bootstraping pants from local sources"
  echo " -d           if running jvm tests, don't use nailgun daemons"
  echo " -j           skip jvm tests"
  echo " -p           skip python tests"

  if (( $# > 0 )); then
    die "$@"
  else
    exit 0
  fi
}

daemons="--ng-daemons"

while getopts "hbdjp" opt; do
  case ${opt} in
    h) usage ;;
    b) skip_bootstrap="true" ;;
    d) daemons="--no-ng-daemons" ;;
    j) skip_java="true" ;;
    p) skip_python="true" ;;
    *) usage "Invalid option: -${OPTARG}" ;;
  esac
done

banner "CI BEGINS"

if [[ "${skip_bootstrap:-false}" == "false" ]]; then
  banner "Bootstrapping pants"
  (
    ./build-support/python/clean.sh && \
    git clean -fdx && \
    PANTS_VERBOSE=1 PEX_VERBOSE=1 PYTHON_VERBOSE=1 ./pants;
    ./pants goal goals
  ) || die "Failed to bootstrap pants."
fi

if [[ "${skip_java:-false}" == "false" ]]; then
  banner "Running jvm tests"
  (
    ./pants goal test {src,tests}/java/com/twitter/common:: $daemons -x && \
    ./pants goal test {src,tests}/scala/com/twitter/common:: $daemons -x
  ) || die "Jvm test failure."
fi

if [[ "${skip_python:-false}" == "false" ]]; then
  banner "Running python tests"
  (
    ./pants goal clean-all && \
    PEX_VERBOSE=5 PYTHON_VERBOSE=1 PANTS_PYTHON_TEST_FAILSOFT=1 \
      ./pants build -v --timeout=5 tests/python/twitter/common:all
  ) || die "Python test failure"
fi

banner "CI SUCCESS"
