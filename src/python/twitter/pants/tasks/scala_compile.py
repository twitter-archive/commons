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

from collections import defaultdict
import os

from twitter.common.dirutil import safe_mkdir
from twitter.pants import get_buildroot, is_scala
from twitter.pants.targets.scala_library import ScalaLibrary
from twitter.pants.targets.scala_tests import ScalaTests
from twitter.pants.targets import resolve_target_sources
from twitter.pants.tasks import TaskError
from twitter.pants.tasks.binary_utils import nailgun_profile_classpath
from twitter.pants.tasks.nailgun_task import NailgunTask

class ScalaCompile(NailgunTask):
  @classmethod
  def setup_parser(cls, option_group, args, mkflag):
    option_group.add_option(mkflag("warnings"), mkflag("warnings", negate=True),
                            dest="scala_compile_warnings", default=True,
                            action="callback", callback=mkflag.set_bool,
                            help="[%default] Compile scala code with all configured warnings "
                                 "enabled.")

  def __init__(self, context, output_dir=None, classpath=None, main=None, args=None, confs=None):
    workdir = context.config.get('scala-compile', 'nailgun_dir')
    NailgunTask.__init__(self, context, workdir=workdir)

    self._compile_profile = context.config.get('scala-compile', 'compile-profile')

    # All scala targets implicitly depend on the selected scala runtime.
    scaladeps = []
    for spec in context.config.getlist('scala-compile', 'scaladeps'):
      scaladeps.extend(context.resolve(spec))
    for target in context.targets(is_scala):
      target.update_dependencies(scaladeps)

    self._compiler_classpath = classpath
    self._output_dir = output_dir or context.config.get('scala-compile', 'workdir')
    self._main = main or context.config.get('scala-compile', 'main')

    self._args = args or context.config.getlist('scala-compile', 'args')
    if context.options.scala_compile_warnings:
      self._args.extend(context.config.getlist('scala-compile', 'warning_args'))
    else:
      self._args.extend(context.config.getlist('scala-compile', 'no_warning_args'))

    self._confs = confs or context.config.getlist('scala-compile', 'confs')
    self._depfile = os.path.join(self._output_dir, 'dependencies')

  def execute(self, targets):
    scala_targets = filter(is_scala, targets)
    if scala_targets:
      with self.context.state('classpath', []) as cp:
        for conf in self._confs:
          cp.insert(0, (conf, self._output_dir))

      with self.changed(scala_targets, invalidate_dependants=True) as changed_targets:
        sources_by_target = self.calculate_sources(changed_targets)
        if sources_by_target:
          sources = reduce(lambda all, sources: all.union(sources), sources_by_target.values())
          if not sources:
            self.context.log.warn('Skipping scala compile for targets with no sources:\n  %s' %
                                  '\n  '.join(str(t) for t in sources_by_target.keys()))
          else:
            classpath = [jar for conf, jar in cp if conf in self._confs]
            result = self.compile(classpath, sources)
            if result != 0:
              raise TaskError('%s returned %d' % (self._main, result))

      if self.context.products.isrequired('classes'):
        genmap = self.context.products.get('classes')

        # Map generated classes to the owning targets and sources.
        compiler = ScalaCompiler(self._output_dir, self._depfile)
        for target, classes_by_source in compiler.findclasses(targets).items():
          for source, classes in classes_by_source.items():
            genmap.add(source, self._output_dir, classes)
            genmap.add(target, self._output_dir, classes)

  def calculate_sources(self, targets):
    sources = defaultdict(set)
    def collect_sources(target):
      src = (os.path.join(target.target_base, source)
             for source in target.sources if source.endswith('.scala'))
      if src:
        sources[target].update(src)

        if (isinstance(target, ScalaLibrary) or isinstance(target, ScalaTests)) and (
            target.java_sources):
          sources[target].update(resolve_target_sources(target.java_sources, '.java'))

    for target in targets:
      collect_sources(target)
    return sources

  def compile(self, classpath, sources):
    safe_mkdir(self._output_dir)

    compiler_classpath = (
      self._compiler_classpath
      or nailgun_profile_classpath(self, self._compile_profile)
    )
    self.ng('ng-cp', *compiler_classpath)

    # TODO(John Sirois): separate compiler profile from runtime profile
    args = [
      '-classpath', ':'.join(compiler_classpath + classpath),
      '-d', self._output_dir,

      # TODO(John Sirois): dependencyfile requires the deprecated -make:XXX - transition to ssc
      '-dependencyfile', self._depfile,
      '-make:transitivenocp'
    ]
    args.extend(self._args)
    args.extend(sources)
    self.context.log.debug('Executing: %s %s' % (self._main, ' '.join(args)))
    return self.ng(self._main, *args)


class ScalaCompiler(object):
  _SECTIONS = ['classpath', 'sources', 'source_to_class']

  def __init__(self, outputdir, depfile):
    self.outputdir = outputdir
    self.depfile = depfile

  def findclasses(self, targets):
    sources = set()
    target_by_source = dict()
    for target in targets:
      for source in target.sources:
        src = os.path.normpath(os.path.join(target.target_base, source))
        target_by_source[src] = target
        sources.add(src)

    classes_by_target_by_source = defaultdict(lambda: defaultdict(set))
    if os.path.exists(self.depfile):
      with open(self.depfile, 'r') as deps:
        section = 0
        for dep in deps.readlines():
          line = dep.strip()
          if '-------' == line:
            section += 1
          elif ScalaCompiler._SECTIONS[section] == 'source_to_class':
            src, cls = line.split('->')
            sourcefile = os.path.relpath(os.path.join(self.outputdir, src.strip()), get_buildroot())
            if sourcefile in sources:
              classfile = os.path.relpath(os.path.join(self.outputdir, cls.strip()), self.outputdir)
              target = target_by_source[sourcefile]
              relsrc = os.path.relpath(sourcefile, target.target_base)
              classes_by_target_by_source[target][relsrc].add(classfile)
    return classes_by_target_by_source
