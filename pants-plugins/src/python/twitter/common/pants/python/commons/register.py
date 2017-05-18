# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

import os

from pants.backend.jvm.repository import Repository
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.task_registrar import TaskRegistrar as task

from twitter.common.pants.python.commons.read_contents import read_contents_factory
from twitter.common.pants.python.commons.version import Version


public_repo = Repository(name='public',
                         url='http://maven.twttr.com',
                         push_db_basedir=os.path.join('build-support', 'commons', 'ivy', 'pushdb'))


def build_file_aliases():
  return BuildFileAliases(
      objects={
          'commons_version': Version('src/python/twitter/common/VERSION').version,
          'public': public_repo,  # key 'public' must match name='public' above)
      },
      context_aware_object_factories={
          'read_contents': read_contents_factory,
      })
