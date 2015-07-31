#!/usr/bin/env bash

# This script publishes all of the python commons locally with the exception of a few
# blacklisted legacy targets.
# NB: To run you'll need a `~/.pypirc` properly configured with credentials for the
# 'twitter' user.

PUBLISH_BLACKLIST=(
  # This is the old monolithic commons target - we no longer wish to publish this since
  # it would contain code duplicate to the individual package slices it contains.
  src/python/twitter/common

  # This is the old pex package, now moved out to the pantsbuild/pex github repo and
  # published from there.
  src/python/twitter/common/python
)

spec_excludes="'[$(echo ${PUBLISH_BLACKLIST[@]} | sed "s| |','|g")']"

./pants \
  --spec-excludes=\"${spec_excludes}\" \
  setup-py \
    --recursive \
    --run="register sdist upload" \
    src/python/twitter/common::
