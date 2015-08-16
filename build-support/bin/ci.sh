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
  echo "Usage: $0 (-h|-bsjp)"
  echo " -h           print out this help message"
  echo " -b           skip bootstraping pants"
  echo " -s           skip distribution tests"
  echo " -j           skip jvm tests"
  echo " -p           skip python tests"

  if (( $# > 0 )); then
    die "$@"
  else
    exit 0
  fi
}

while getopts "hbsjp" opt; do
  case ${opt} in
    h) usage ;;
    b) skip_bootstrap="true" ;;
    s) skip_distribution="true" ;;
    j) skip_java="true" ;;
    p) skip_python="true" ;;
    *) usage "Invalid option: -${OPTARG}" ;;
  esac
done

# Pants does not work with 3.0-3.2 due to the unicode
# flip-flop that settled out at 3.3.  Make sure travis-ci
# for example does not attempt to setup a 3.2 interpreter.
INTERPRETER_CONSTRAINTS=(
  "CPython>=2.6,<3"
  "CPython>=3.3"
)
for constraint in ${INTERPRETER_CONSTRAINTS[@]}; do
  INTERPRETER_ARGS=(
    ${INTERPRETER_ARGS[@]}
    --interpreter="${constraint}"
  )
done

banner "CI BEGINS"

if [[ "${skip_bootstrap:-false}" == "false" ]]; then
  banner "Bootstrapping pants"
  (
    ./pants help
  ) || die "Failed to bootstrap pants."
fi

./pants clean-all || die "Failed to clean-all."

if [[ "${skip_distribution:-false}" == "false" ]]; then
  banner "Running distribution tests"
  # TODO(John Sirois): re-stablish this branch as testing jvm and python commons distributions can
  # be built.
fi

if [[ "${skip_java:-false}" == "false" ]]; then
  banner "Running jvm tests"
  (
    ./pants -x ${INTERPRETER_ARGS[@]} test {src,tests}/java/com/twitter/common:: && \
    ./pants -x ${INTERPRETER_ARGS[@]} test {src,tests}/scala/com/twitter/common::
  ) || die "Jvm test failure."
fi

if [[ "${skip_python:-false}" == "false" ]]; then
  banner "Compiling python libraries and binaries"
  (
    compile_exclusions=(
      # The app/modules design forces a cycle between app and modules that must be broken
      # by modules not depending on app.
      src/python/twitter/common/app/modules

      # These support legacy zookeeper client via the c bindings and only have their zookeeper
      # dep activated by an extra_requires on pip install.
      src/python/twitter/common/zookeeper/group:group
      src/python/twitter/common/zookeeper/serverset:cli
      src/python/twitter/common/zookeeper:zookeeper-old

      # These are source-only targets and each have a dependencies-only target that is used
      # to get the proper dep set.
      src/python/twitter/common/rpc/sasl:sourceonly
      src/python/twitter/common/python:python-source
    )
    ./pants \
      ${compile_exclusions[@]/#/--exclude-target-regexp=} \
      compile.python-eval --fail-slow src/python/::
  ) || die "Python compile failure"

  banner "Running python tests"
  (
    # TODO(John Sirois): We clean-all here to work-around args resource mapper issues finding leftover
    # entries from args tests in the jvm tests above, kill the clean-all once the resource mapper bug
    # is identified and fixed.
    ./pants --timeout=5 ${INTERPRETER_ARGS[@]} clean-all test.pytest --fail-slow --no-fast \
      tests/python/twitter/common:all
  ) || die "Python test failure"
fi

banner "CI SUCCESS"
