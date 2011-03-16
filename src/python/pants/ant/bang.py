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

from pants import (
  JavaLibrary,
  JavaProtobufLibrary,
  JavaTarget,
  JavaTests,
  JavaThriftLibrary,
  ScalaLibrary,
  ScalaTests,
)

def extract_target(java_target):
  """Extracts a minimal set of linked targets from the given target's internal transitive dependency
  set.  The root target in the extracted target set is returned.  The algorithm does a topological
  sort of the internal targets and then tries to coalesce targets of a given type.  Any target with
  a custom ant build xml will be excluded from the coalescing."""

  coalesced = java_target.coalesce()
  if not coalesced:
    return java_target

  coalesced.insert(0, java_target)
  coalesced = list(reversed(coalesced))

  name = "fast-%s" % java_target.name
  provides = None
  deployjar = hasattr(java_target, 'deployjar') and java_target.deployjar
  buildflags = java_target.buildflags

  def create_target(target_type, target_name, target_index, targets):
    def name(name):
      return "%s-%s-%d" % (target_name, name, target_index)

    if target_type == JavaProtobufLibrary:
      return JavaProtobufLibrary._aggregate(name('protobuf'), provides, buildflags, targets)
    elif target_type == JavaThriftLibrary:
      return JavaThriftLibrary._aggregate(name('thrift'), provides, buildflags, targets)
    elif target_type == JavaLibrary:
      return JavaLibrary._aggregate(name('java'), provides, deployjar, buildflags, targets)
    elif target_type == ScalaLibrary:
      return ScalaLibrary._aggregate(name('scala'), provides, deployjar, buildflags, targets)
    elif target_type == JavaTests:
      return JavaTests._aggregate(name('java-tests'), buildflags, targets)
    elif target_type == ScalaTests:
      return ScalaTests._aggregate(name('scala-tests'), buildflags, targets)
    else:
      raise Exception("Cannot aggregate targets of type: %s" % target_type)

  # chunk up our targets by type & custom build xml
  start_type = type(coalesced[0])
  start = 0
  descriptors = []

  for current in range(0, len(coalesced)):
    current_target = coalesced[current]
    current_type = type(current_target)

    if current_target.custom_antxml_path:
      if start < current:
        # if we have a type chunk to our left, record it
        descriptors.append((start_type, coalesced[start:current]))

      # record a chunk containing just the target that has the custom build xml to be conservative
      descriptors.append((current_type, [current_target]))
      start = current + 1
      if current < (len(coalesced) - 1):
        start_type = type(coalesced[start])

    elif start_type != current_type:
      # record the type chunk we just left
      descriptors.append((start_type, coalesced[start:current]))
      start = current
      start_type = current_type

  if start < len(coalesced):
    # record the tail chunk
    descriptors.append((start_type, coalesced[start:]))

  # build meta targets aggregated from the chunks and keep track of which targets end up in which
  # meta targets
  meta_targets_by_target_id = dict()
  targets_by_meta_target = []
  parent_meta_target = None
  for (target_type, targets), index in zip(descriptors, reversed(range(0, len(descriptors)))):
    parent_meta_target = create_target(target_type, name, index, targets)
    targets_by_meta_target.append((parent_meta_target, targets))
    for target in targets:
      meta_targets_by_target_id[target._id] = parent_meta_target

  # calculate the other meta-targets (if any) each meta-target depends on
  extra_targets_by_meta_target = []
  for meta_target, targets in targets_by_meta_target:
    meta_deps = set()
    custom_antxml_path = None
    for target in targets:
      if target.custom_antxml_path:
        custom_antxml_path = target.custom_antxml_path
      for dep in target.resolved_dependencies:
        if isinstance(dep, JavaTarget):
          meta = meta_targets_by_target_id[dep._id]
          if meta != meta_target:
            meta_deps.add(meta)
    extra_targets_by_meta_target.append((meta_target, meta_deps, custom_antxml_path))

  def lift_excludes(meta_target):
    excludes = set()
    def lift(target):
      if target.excludes:
        excludes.update(target.excludes)
      for jar_dep in target.jar_dependencies:
        excludes.update(jar_dep.excludes)
      for internal_dep in target.internal_dependencies:
        lift(internal_dep)
    lift(meta_target)
    return excludes

  # link in the extra inter-meta deps
  for meta_target, extra_deps, custom_antxml_path in extra_targets_by_meta_target:
    for dep in extra_deps:
      meta_target.jar_dependencies.update(dep._as_jar_dependencies())
    meta_target.internal_dependencies.update(extra_deps)
    meta_target.excludes = lift_excludes(meta_target)
    meta_target.custom_antxml_path = custom_antxml_path

  return parent_meta_target
