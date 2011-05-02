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

__author__ = 'John Sirois'

from common.collections import OrderedSet
from ide import Ide
from pants import (
  Generator,
  TemplateData,
)

import collections
import os
import shutil
import urllib

_TEMPLATE_BASEDIR = os.path.join(os.path.dirname(__file__), 'eclipse/templates')

_VERSIONS = {
  '3.5': '3.5', # 3.5 and 3.6 are .project/.classpath compatible
  '3.6': '3.5',
}

class Eclipse(Ide):
  """Creates Eclipse projects for a set of BUILD targets."""

  def setup_parser(self, parser):
    Ide.setup_parser(self, parser)

    supported_versions = list(_VERSIONS.keys())
    supported_versions.sort()
    parser.add_option("-i", "--eclipse-version", dest = "version",
                      default = '3.5', type = "choice", choices = supported_versions,
                      help = "[%%default] The Eclipse version the project configuration "
                      "should be generated for; can be one of: %s" % supported_versions)

    parser.add_option("--eclipse-build-output-dir", dest = "eclipse_output_dir",
                      default = os.path.join(self.root_dir, 'target/eclipse/bin'),
                      help = "[%default] Specifies the directory Eclipse should use for its own "
                      "build output.")

    parser.epilog = """Creates an Eclipse project appropriate for editing, debugging and testing
    the specified BUILD targets."""

  def __init__(self, root_dir, parser, argv):
    Ide.__init__(self, root_dir, parser, argv)

    eclipse_version = _VERSIONS[self.options.version]
    self.project_template = os.path.join(_TEMPLATE_BASEDIR, 'project-%s.mk' % eclipse_version)
    self.classpath_template = os.path.join(_TEMPLATE_BASEDIR, 'classpath-%s.mk' % eclipse_version)
    self.pydev_template = os.path.join(_TEMPLATE_BASEDIR, 'pydevproject-%s.mk' % eclipse_version)

    self.project_name = self.options.project_name

    self.project_filename = os.path.join(self.root_dir, '.project')
    self.classpath_filename = os.path.join(self.root_dir, '.classpath')
    self.pydev_filename = os.path.join(self.root_dir, '.pydevproject')

  def _generate_project_files(self, project, ivyfile, ivysettingsfile):
    def create_sourcepath(base, sources):
      def normalize_path_pattern(path):
        return '%s/' % path if not path.endswith('/') else path

      includes = [ normalize_path_pattern(source_set.path) for source_set in sources ]
      excludes = []
      for source_set in sources:
        excludes.extend(normalize_path_pattern(exclude) for exclude in source_set.excludes)

      return TemplateData(
        base = base,
        includes = includes,
        excludes = excludes,
      )

    configured_project = TemplateData(
      name = self.project_name,
      has_python = project.has_python
    )

    ivyconfig = dict(
      ivyXmlPath = os.path.relpath(ivyfile, project.root_dir),
      confs = '*',
      ivySettingsPath = 'file://%s' % ivysettingsfile,
      loadSettingsOnDemand = 'false',
      propertyFiles = '',
      acceptedTypes = 'jar,bundle,ejb,maven-plugin',
      sourceTypes = 'source',
      javadocTypes = '',
      sourceSuffixes = '',
      javadocSuffixes = '',
      alphaOrder = 'true',
      resolveInWorkspace = 'false',
      resolveBeforeLaunch = 'false',
    )

    outdir = os.path.abspath(self.options.eclipse_output_dir)
    if not os.path.exists(outdir):
      os.makedirs(outdir)

    source_sets = collections.defaultdict(OrderedSet) # base -> source_set
    for source_set in project.sources:
      source_sets[source_set.source_base].add(source_set)

    configured_classpath = TemplateData(
      sourcepaths = [ create_sourcepath(base, sources) for base, sources in source_sets.items() ],
      has_tests = project.has_tests,
      has_ivy = True,
      ivyconfig = urllib.urlencode(ivyconfig).replace('&', '&amp;'),
      outdir = os.path.relpath(outdir, self.root_dir),
    )

    with open(self.project_filename, 'w') as output:
      Generator(self.project_template, project = configured_project).write(output)

    with open(self.classpath_filename, 'w') as output:
      Generator(self.classpath_template, classpath = configured_classpath).write(output)

    if os.path.exists(self.pydev_filename):
      os.remove(self.pydev_filename)

    if project.has_python:
      with open(self.pydev_filename, 'w') as output:
        Generator(self.pydev_template, project = configured_project).write(output)
