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

try:
  from mako.template import Template
except ImportError:
  exit("""pants requires mako to run.

You can find mako here: http://www.makotemplates.org/

If you have easy_install, you can install with:
$ sudo easy_install mako

If python 2.6 is not your platform default, then:
$ sudo easy_install-2.6 mako

If you have pip, use:
$ sup pip install mako

If you're seeing this message again after having already installed mako, its
likely root and your user are using different versions of python.  You can
probably fix the issue by ensuring root's version of python is selected 1st on
your user account's PATH.
""")

import os

class TemplateData(object):
  """Encapsulates data for a mako template as a property-addressable map-like struct."""

  def __init__(self, **kwargs):
    self._props = kwargs.copy()

  def extend(self, **kwargs):
    """Returns a new TemplateData with this template's data overlayed by the key value pairs
    specified as keyword arguments."""

    props = self._props.copy()
    props.update(kwargs)
    return TemplateData(**props)

  def __getattr__(self, key):
    return self._props[key]

  def __eq__(self, other):
    result = other and (
      type(other) == TemplateData) and (
      self._props == other._props)
    return result

  def __hash__(self):
    return hash(self._props)

  def __ne__(self, other):
    return not self.__eq__(other)

  def __repr__(self):
    return "TemplateData(%s)" % self._props

class Generator(object):
  """Generates pants intermediary output files using a configured mako template."""

  _module_directory = '/tmp/pants-%s' % os.environ['USER']

  def __init__(self, template_path, root_dir, template_data):
    self._template = Template(filename = template_path,
                              module_directory = Generator._module_directory)
    self._root_dir = root_dir
    self._template_data = template_data

  def write(self, stream):
    """Applies the template to the template data and writes the result to the given file-like
    stream."""

    stream.write(self._template.render(root_dir = self._root_dir, lib = self._template_data))

class Builder(object):
  """Abstract base class for builder implementations that can execute a parsed BUILD target."""

  def __init__(self, ferror, root_dir):
    object.__init__(self)

    self.ferror = ferror
    self.root_dir = root_dir

  def build(self, target, is_meta, args):
    """Subclasses must implement a BUILD target executor.  The value returned should be an int,
    0 indicating success and any other value indicating failure.

    target: the parsed target to build
    is_meta: True if the meta build flag is set
    args: additional arguments to the builder backend"""

    pass
