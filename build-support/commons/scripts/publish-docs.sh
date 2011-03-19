#!/bin/bash

MY_DIR=$(dirname $0)
BUILD_ROOT=$MY_DIR/../../..
PANTS=$BUILD_ROOT/pants

ref=$(git symbolic-ref head)
branch=${ref#refs/heads/}
sha=$(git rev-list head | head -1)

$PANTS doc --title="Twitter Commons API Docs" && \
  git checkout gh-pages && \
  git pull && \
  rm -r apidocs && \
  mv target/pants.doc apidocs && \
  git add apidocs && \
  git commit -m "Update Twitter Commons API Docs @ $sha" && \
  git push origin head && \
  git checkout $branch

