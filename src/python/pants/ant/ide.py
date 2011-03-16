# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from collections import deque
from common.collections import OrderedSet
from copy import copy
from pants import (
  JavaLibrary,
  JavaTests,
  ScalaTests
)

import bang

def extract_target(java_target, is_transitive):
  meta_target = bang.extract_target(java_target)

  internal_deps, jar_deps = _extract_target(meta_target, is_transitive)

  # TODO(John Sirois): make an empty source set work in ant/compile.xml
  sources = [ '__no_source__' ]

  all_deps = OrderedSet()
  all_deps.update(internal_deps)
  all_deps.update(jar_deps)

  return JavaLibrary('ide',
                     sources,
                     provides = None,
                     dependencies = all_deps,
                     excludes = meta_target.excludes,
                     resources = None,
                     binary_resources = None,
                     deployjar = False,
                     buildflags = None,
                     is_meta = True)

def _extract_target(meta_target, is_transitive):
  class RootNode(object):
    def __init__(self):
      self.internal_dependencies = OrderedSet()

  root_target = RootNode()

  codegen_graph = deque([])
  codegen_graph.appendleft(root_target)
  jar_deps = OrderedSet()

  def sift_targets(target):
    if target.is_codegen:
      codegen_graph[0].internal_dependencies.add(target)
    else:
      for jar_dependency in target.jar_dependencies:
        if jar_dependency.rev:
          if is_transitive(target):
            jar_deps.add(jar_dependency)
          else:
            jar_deps.add(copy(jar_dependency).intransitive())

    if target.is_codegen:
        codegen_graph.appendleft(target)

    for internal_target in list(target.internal_dependencies):
      target.internal_dependencies.discard(internal_target)
      sift_targets(internal_target)

    if target.is_codegen:
      codegen_graph.popleft()

  sift_targets(meta_target)

  assert len(codegen_graph) == 1 and codegen_graph[0] == root_target,\
    "Unexpected walk: %s" % codegen_graph

  return codegen_graph.popleft().internal_dependencies, jar_deps
