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

import os
import shlex

from collections import defaultdict
import itertools

from twitter.common.dirutil import safe_open, safe_mkdir, safe_rmtree

from twitter.pants import Task
from twitter.pants.base.target import Target
from twitter.pants.base.workunit import WorkUnit
from twitter.pants.reporting.reporting_utils import items_to_report_element
from twitter.pants.tasks import TaskError
from twitter.pants.tasks.jvm_compile import JvmCompile
from twitter.pants.tasks.jvm_compiler_dependencies import Dependencies


# Well known metadata file to auto-register annotation processors with a java 1.6+ compiler
_PROCESSOR_INFO_FILE = 'META-INF/services/javax.annotation.processing.Processor'


_JMAKE_MAIN = 'com.sun.tools.jmake.Main'


# From http://kenai.com/projects/jmake/sources/mercurial/content/src/com/sun/tools/jmake/Main.java?rev=26
# Main.mainExternal docs.
_JMAKE_ERROR_CODES = {
   -1: 'invalid command line option detected',
   -2: 'error reading command file',
   -3: 'project database corrupted',
   -4: 'error initializing or calling the compiler',
   -5: 'compilation error',
   -6: 'error parsing a class file',
   -7: 'file not found',
   -8: 'I/O exception',
   -9: 'internal jmake exception',
  -10: 'deduced and actual class name mismatch',
  -11: 'invalid source file extension',
  -12: 'a class in a JAR is found dependent on a class with the .java source',
  -13: 'more than one entry for the same class is found in the project',
  -20: 'internal Java error (caused by java.lang.InternalError)',
  -30: 'internal Java error (caused by java.lang.RuntimeException).'
}
# When executed via a subprocess return codes will be treated as unsigned
_JMAKE_ERROR_CODES.update((256+code, msg) for code, msg in _JMAKE_ERROR_CODES.items())


class JavaCompile(JvmCompile):
  _language = 'java'

  @classmethod
  def setup_parser(cls, option_group, args, mkflag):
    JvmCompile.setup_parser(JavaCompile, option_group, args, mkflag)

    option_group.add_option(mkflag("warnings"), mkflag("warnings", negate=True),
                            dest="java_compile_warnings", default=True,
                            action="callback", callback=mkflag.set_bool,
                            help="[%default] Compile java code with all configured warnings "
                                 "enabled.")

    option_group.add_option(mkflag("args"), dest="java_compile_args", action="append",
                            help="Pass these extra args to javac.")

    option_group.add_option(mkflag("partition-size-hint"), dest="java_compile_partition_size_hint",
                            action="store", type="int", default=-1,
                            help="Roughly how many source files to attempt to compile together. Set"
                                 " to a large number to compile all sources together. Set this to 0"
                                 " to compile target-by-target. Default is set in pants.ini.")

  def __init__(self, context):
    JvmCompile.__init__(self, context, workdir=context.config.get('java-compile', 'nailgun_dir'))

    if context.options.java_compile_partition_size_hint != -1:
      self._partition_size_hint = context.options.java_compile_partition_size_hint
    else:
      self._partition_size_hint = context.config.getint('java-compile', 'partition_size_hint',
                                                        default=1000)

    workdir = context.config.get('java-compile', 'workdir')
    self._classes_dir = os.path.join(workdir, 'classes')
    self._resources_dir = os.path.join(workdir, 'resources')
    self._depfile_dir = os.path.join(workdir, 'depfiles')
    self._depfile = os.path.join(self._depfile_dir, 'global_depfile')

    safe_mkdir(self._classes_dir)
    safe_mkdir(self._depfile_dir)

    self._jmake_bootstrap_key = 'jmake'
    external_tools = context.config.getlist('java-compile', 'jmake-bootstrap-tools', default=[':jmake'])
    self._bootstrap_utils.register_jvm_build_tools(self._jmake_bootstrap_key, external_tools)

    self._compiler_bootstrap_key = 'java-compiler'
    compiler_bootstrap_tools = context.config.getlist('java-compile', 'compiler-bootstrap-tools',
                                                      default=[':java-compiler'])
    self._bootstrap_utils.register_jvm_build_tools(self._compiler_bootstrap_key, compiler_bootstrap_tools)

    self._opts = context.config.getlist('java-compile', 'args')
    self._jvm_args = context.config.getlist('java-compile', 'jvm_args')

    self._javac_opts = []
    if context.options.java_compile_args:
      for arg in context.options.java_compile_args:
        self._javac_opts.extend(shlex.split(arg))
    else:
      self._javac_opts.extend(context.config.getlist('java-compile', 'javac_args', default=[]))

    if context.options.java_compile_warnings:
      self._opts.extend(context.config.getlist('java-compile', 'warning_args'))
    else:
      self._opts.extend(context.config.getlist('java-compile', 'no_warning_args'))

    self._confs = context.config.getlist('java-compile', 'confs')
    self.context.products.require_data('exclusives_groups')

    self.setup_artifact_cache_from_config(config_section='java-compile')

    # A temporary, but well-known, dir to munge analysis files in before caching. It must be
    # well-known so we know where to find the files when we retrieve them from the cache.
    self._depfile_tmpdir = os.path.join(self._depfile_dir, 'depfile_tmpdir')

  def product_type(self):
    return 'classes'

  def can_dry_run(self):
    return True

  def _ensure_depfile_tmpdir(self):
    # Do this lazily, so we don't trigger creation of a worker pool unless we need it.
    if not os.path.exists(self._depfile_tmpdir):
      os.makedirs(self._depfile_tmpdir)
      self.context.background_worker_pool().add_shutdown_hook(lambda: safe_rmtree(self._depfile_tmpdir))

  def execute(self, targets):
    java_targets = [t for t in targets if t.has_sources('.java')]
    
    if not java_targets:
      return

    # Get the exclusives group for the targets to compile.
    # Group guarantees that they'll be a single exclusives key for them.
    egroups = self.context.products.get_data('exclusives_groups')
    group_id = egroups.get_group_key_for_target(java_targets[0])

    # Add classes and resource dirs to the classpath for us and for downstream tasks.
    for conf in self._confs:
      egroups.update_compatible_classpaths(group_id, [(conf, self._classes_dir)])
      egroups.update_compatible_classpaths(group_id, [(conf, self._resources_dir)])

    # Get the classpath generated by upstream JVM tasks (including previous calls to execute()).
    cp = egroups.get_classpath_for_group(group_id)

    with self.invalidated(java_targets, invalidate_dependents=True,
                          partition_size_hint=self._partition_size_hint) as invalidation_check:
      if not self.dry_run:
        for vts in invalidation_check.invalid_vts_partitioned:
          # Compile, using partitions for efficiency.
          sources_by_target = self._process_target_partition(vts, cp)

          # TODO: Check for missing dependencies.  See ScalaCompile for an example.
          # Will require figuring out what the actual deps of a class file are.

          vts.update()
          if self.artifact_cache_writes_enabled():
            self._write_to_artifact_cache(vts, sources_by_target)

        # Provide the target->class and source->class mappings to downstream tasks if needed.
        if self.context.products.isrequired('classes'):
          if os.path.exists(self._depfile):
            sources_by_target = self._compute_sources_by_target(java_targets)
            deps = Dependencies(self._classes_dir)
            deps.load(self._depfile)
            self._add_all_products_to_genmap(sources_by_target, deps.classes_by_source)

        # Produce a monolithic apt processor service info file for further compilation rounds
        # and the unit test classpath.
        all_processors = set()
        for target in java_targets:
          if target.is_apt and target.processors:
            all_processors.update(target.processors)
        processor_info_file = os.path.join(self._classes_dir, _PROCESSOR_INFO_FILE)
        if os.path.exists(processor_info_file):
          with safe_open(processor_info_file, 'r') as f:
            for processor in f:
              all_processors.add(processor.strip())
        self.write_processor_info(processor_info_file, all_processors)

  def _process_target_partition(self, vts, cp):
    sources_by_target = self._compute_sources_by_target(vts.targets)
    sources = list(itertools.chain.from_iterable(sources_by_target.values()))
    fingerprint = Target.identify(vts.targets)

    if not sources:
      self.context.log.warn('Skipping java compile for targets with no sources:\n  %s' % vts.targets)
    else:
      # Do some reporting.
      self.context.log.info(
        'Operating on a partition containing ',
        items_to_report_element(vts.cache_key.sources, 'source'),
        ' in ',
        items_to_report_element([t.address.reference() for t in vts.targets], 'target'), '.')
      classpath = [jar for conf, jar in cp if conf in self._confs]
      result = self.compile(classpath, sources, fingerprint, self._depfile)
      if result != 0:
        default_message = 'Unexpected error - %s returned %d' % (_JMAKE_MAIN, result)
        raise TaskError(_JMAKE_ERROR_CODES.get(result, default_message))
    return sources_by_target

  def _write_to_artifact_cache(self, vts, sources_by_target):
    self._ensure_depfile_tmpdir()
    vt_by_target = dict([(vt.target, vt) for vt in vts.versioned_targets])

    # This work can happen in the background, if there's a measurable benefit to that.

    # Split the depfile into per-target files.
    splits = [(sources, JavaCompile.create_depfile_path(self._depfile_tmpdir, [target]))
              for target, sources in sources_by_target.items()]
    deps = Dependencies(self._classes_dir)
    if os.path.exists(self._depfile):
      deps.load(self._depfile)
    deps.split(splits)

    # Gather up the artifacts.
    vts_artifactfiles_pairs = []
    for target, sources in sources_by_target.items():
      artifacts = [JavaCompile.create_depfile_path(self._depfile_tmpdir, [target])]
      for source in sources:
        for cls in deps.classes_by_source.get(source, []):
          artifacts.append(os.path.join(self._classes_dir, cls))
      vt = vt_by_target.get(target)
      if vt is not None:
        vts_artifactfiles_pairs.append((vt, artifacts))

    # Write to the artifact cache.
    self.update_artifact_cache(vts_artifactfiles_pairs)

  def check_artifact_cache(self, vts):
    # Special handling for java depfiles. Class files are retrieved directly into their
    # final locations in the global classes dir.

    def post_process_cached_vts(cached_vts):
      # Merge the cached analyses into the existing global one.
      if cached_vts:
        with self.context.new_workunit(name='merge-dependencies'):
          global_deps = Dependencies(self._classes_dir)
          if os.path.exists(self._depfile):
            global_deps.load(self._depfile)
          for vt in cached_vts:
            for target in vt.targets:
              depfile = JavaCompile.create_depfile_path(self._depfile_tmpdir, [target])
              if os.path.exists(depfile):
                deps = Dependencies(self._classes_dir)
                deps.load(depfile)
                global_deps.merge(deps)
          global_deps.save(self._depfile)

    self._ensure_depfile_tmpdir()
    return Task.do_check_artifact_cache(self, vts, post_process_cached_vts=post_process_cached_vts)

  @staticmethod
  def create_depfile_path(depfile_dir, targets):
    compilation_id = Target.maybe_readable_identify(targets)
    return os.path.join(depfile_dir, compilation_id) + '.dependencies'

  def _compute_sources_by_target(self, targets):
    sources_by_target = defaultdict(set)
    def collect_sources(target):
      src = (os.path.join(target.target_base, source)
             for source in target.sources if source.endswith('.java'))
      if src:
        sources_by_target[target].update(src)

    for target in targets:
      collect_sources(target)
    return sources_by_target

  def compile(self, classpath, sources, fingerprint, depfile):
    jmake_classpath = self._bootstrap_utils.get_jvm_build_tools_classpath(self._jmake_bootstrap_key,
                                                                          self.runjava_indivisible)
    opts = [
      '-classpath', ':'.join(classpath),
      '-d', self._classes_dir,
      '-pdb', os.path.join(self._classes_dir, '%s.dependencies.pdb' % fingerprint),
    ]

    compiler_classpath = self._bootstrap_utils.get_jvm_build_tools_classpath(self._compiler_bootstrap_key,
                                                                             self.runjava_indivisible)
    opts.extend([
      '-jcpath', ':'.join(compiler_classpath),
      '-jcmainclass', 'com.twitter.common.tools.Compiler',
      '-C-Tdependencyfile', '-C%s' % depfile,
    ])
    opts.extend(map(lambda arg: '-C%s' % arg, self._javac_opts))

    opts.extend(self._opts)
    return self.runjava_indivisible(_JMAKE_MAIN,
                                    classpath=jmake_classpath,
                                    opts=opts,
                                    args=sources,
                                    jvmargs=self._jvm_args,
                                    workunit_name='jmake',
                                    workunit_labels=[WorkUnit.COMPILER])

  def write_processor_info(self, processor_info_file, processors):
    with safe_open(processor_info_file, 'w') as f:
      for processor in processors:
        f.write('%s\n' % processor)

  def _compute_classes_by_source(self, depfile=None):
    """Compute src->classes."""
    if depfile is None:
      depfile = self._depfile

    if not os.path.exists(depfile):
      return {}
    deps = Dependencies(self._classes_dir)
    deps.load(depfile)
    return deps.classes_by_source

  def _add_all_products_to_genmap(self, sources_by_target, classes_by_source):
    # Map generated classes to the owning targets and sources.
    genmap = self.context.products.get('classes')
    for target, sources in sources_by_target.items():
      for source in sources:
        classes = classes_by_source.get(source, [])
        relsrc = os.path.relpath(source, target.target_base)
        genmap.add(relsrc, self._classes_dir, classes)
        genmap.add(target, self._classes_dir, classes)

      # TODO(John Sirois): Map target.resources in the same way
      # 'Map' (rewrite) annotation processor service info files to the owning targets.
      if target.is_apt and target.processors:
        basedir = os.path.join(self._resources_dir, Target.maybe_readable_identify([target]))
        processor_info_file = os.path.join(basedir, _PROCESSOR_INFO_FILE)
        self.write_processor_info(processor_info_file, target.processors)
        genmap.add(target, basedir, [_PROCESSOR_INFO_FILE])
