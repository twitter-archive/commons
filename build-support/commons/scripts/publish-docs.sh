#!/bin/bash
# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

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

