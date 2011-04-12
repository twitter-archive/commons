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

import fnmatch
import glob
import os

from pants import (
  ExportableJvmLibrary,
  InternalTarget,
  JvmTarget,
  JavaLibrary,
  JavaProtobufLibrary,
  JavaTests,
  JavaThriftLibrary,
  PythonTarget,
  PythonTests,
  ScalaLibrary,
  ScalaTests,
  TargetWithSources,
)

class Fileset(object):
  """A callable object that will gather up a set of files lazily when called.  Supports unions with
  iterables, other Filesets and individual items using the ^ and + operators as well as set
  difference using the - operator."""

  def __init__(self, callable):
    self._callable = callable

  def __call__(self, *args, **kwargs):
    return self._callable(*args, **kwargs)

  def __add__(self, other):
    return self ^ other

  def __xor__(self, other):
    def union():
      if callable(other):
        return self() ^ other()
      elif isinstance(other, set):
        return self() ^ other
      else:
        try:
          return self() ^ set(iter(other))
        except:
          return self().add(other)
    return Fileset(union)

  def __sub__(self, other):
    def subtract():
      if callable(other):
        return self() - other()
      elif isinstance(other, set):
        return self() - other
      else:
        try:
          return self() - set(iter(other))
        except:
          return self().remove(other)
    return Fileset(subtract)

def globs(*globspecs):
  """Returns a Fileset that combines the lists of files returned by glob.glob for each globspec."""

  def combine(files, globspec):
    return files ^ set(glob.glob(globspec))
  return Fileset(lambda: reduce(combine, globspecs, set()))

def rglobs(*globspecs):
  """Returns a Fileset that does a recursive scan under the current directory combining the lists of
  files returned that would be returned by glob.glob for each globspec."""

  root = os.curdir
  def recursive_globs():
    for base, dirs, files in os.walk(root):
      for file in files:
        path = os.path.relpath(os.path.normpath(os.path.join(base, file)), root)
        for globspec in globspecs:
          if fnmatch.fnmatch(path, globspec):
            yield path

  return Fileset(lambda: set(recursive_globs()))

def has_sources(target):
  """Returns True if the target has sources."""

  return isinstance(target, TargetWithSources)

def is_exported(target):
  """Returns True if the target provides an artifact exportable from the repo."""

  return isinstance(target, ExportableJvmLibrary) and target.provides

def is_internal(target):
  """Returns True if the target is internal to the repo (ie: it might have dependencies)."""

  return isinstance(target, InternalTarget)

def is_jvm(target):
  """Returns True if the target produces jvm bytecode."""

  return isinstance(target, JvmTarget)

def is_java(target):
  """Returns True if the target has or generates java sources."""

  return isinstance(target, JavaLibrary) or (
    isinstance(target, JavaProtobufLibrary)) or (
    isinstance(target, JavaTests)) or (
    isinstance(target, JavaThriftLibrary))

def is_python(target):
  """Returns True if the target has python sources."""

  return isinstance(target, PythonTarget)

def is_scala(target):
  """Returns True if the target has scala sources."""

  return isinstance(target, ScalaLibrary) or isinstance(target, ScalaTests)

def is_test(t):
  """Returns True if the target is comprised of tests."""

  return isinstance(t, JavaTests) or isinstance(t, ScalaTests) or isinstance(t, PythonTests)
