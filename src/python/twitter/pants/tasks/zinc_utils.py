# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

__author__ = 'Benjy Weinberger'

import os
import shutil
import textwrap

from contextlib import closing
from xml.etree import ElementTree

from twitter.common.collections import OrderedDict
from twitter.common.contextutil import open_zip as open_jar, temporary_file_path
from twitter.common.dirutil import  safe_open

from twitter.pants import get_buildroot
from twitter.pants.tasks import TaskError
from twitter.pants.tasks.binary_utils import find_java_home, profile_classpath


# Well known metadata file required to register scalac plugins with nsc.
_PLUGIN_INFO_FILE = 'scalac-plugin.xml'

class ZincUtils(object):
  def __init__(self, context, java_runner, color):
    self._context = context
    self._java_runner = java_runner
    self._color = color

    self._pants_home = get_buildroot()

    self._compile_profile = context.config.get('scala-compile', 'compile-profile')  # The target scala version.
    self._zinc_profile = context.config.get('scala-compile', 'zinc-profile')
    self._plugins_profile = context.config.get('scala-compile', 'scalac-plugins-profile')

    self._main = context.config.get('scala-compile', 'main')
    self._scalac_args = context.config.getlist('scala-compile', 'args')
    self._jvm_args = context.config.getlist('scala-compile', 'jvm_args')

    if context.options.scala_compile_warnings:
      self._scalac_args.extend(context.config.getlist('scala-compile', 'warning_args'))
    else:
      self._scalac_args.extend(context.config.getlist('scala-compile', 'no_warning_args'))

    def classpath_for_profile(profile):
      return profile_classpath(profile, java_runner=self._java_runner, config=self._context.config)

    self._zinc_classpath = classpath_for_profile(self._zinc_profile)
    self._compiler_classpath = classpath_for_profile(self._compile_profile)
    self._plugin_jars = classpath_for_profile(self._plugins_profile) if self._plugins_profile else []

    zinc_jars = ZincUtils.identify_zinc_jars(self._compiler_classpath, self._zinc_classpath)
    self._zinc_jar_args = []
    for (name, jarpath) in zinc_jars.items():  # The zinc jar names are also the flag names.
      self._zinc_jar_args.extend(['-%s' % name, jarpath])

    # Allow multiple flags and also comma-separated values in a single flag.
    plugin_names = [p for val in context.options.plugins for p in val.split(',')] \
      if context.options.plugins is not None \
      else context.config.getlist('scala-compile', 'scalac-plugins', default=[])
    plugin_args = context.config.getdict('scala-compile', 'scalac-plugin-args', default={})
    active_plugins = self.find_plugins(plugin_names)

    for name, jar in active_plugins.items():
      self._scalac_args.append('-Xplugin:%s' % jar)
      for arg in plugin_args.get(name, []):
        self._scalac_args.append('-P:%s:%s' % (name, arg))

    # For localizing/relativizing analysis files.
    self._java_home = os.path.dirname(find_java_home())
    self._ivy_home = context.config.get('ivy', 'cache_dir')

  def plugin_jars(self):
    """The jars containing code for enabled plugins."""
    return self._plugin_jars

  def run_zinc(self, args):
    zinc_args = [
      '-log-level', self._context.options.log_level or 'info',
      ]
    if not self._color:
      zinc_args.append('-no-color')
    zinc_args.extend(self._zinc_jar_args)
    zinc_args.extend(args)
    return self._java_runner(self._main, classpath=self._zinc_classpath, args=zinc_args, jvmargs=self._jvm_args)

  def compile(self, classpath, sources, output_dir, analysis_cache, upstream_analysis_caches, depfile):
    # To pass options to scalac simply prefix with -S.
    args = ['-S' + x for x in self._scalac_args]

    def analysis_cache_full_path(analysis_cache_product):
      # We expect the argument to be { analysis_cache_dir, [ analysis_cache_file ]}.
      if len(analysis_cache_product) != 1:
        raise TaskError('There can only be one analysis cache file per output directory')
      analysis_cache_dir, analysis_cache_files = analysis_cache_product.iteritems().next()
      if len(analysis_cache_files) != 1:
        raise TaskError('There can only be one analysis cache file per output directory')
      return os.path.join(analysis_cache_dir, analysis_cache_files[0])

    # Strings of <output dir>:<full path to analysis cache file for the classes in that dir>.
    analysis_map =\
    OrderedDict([ (k, analysis_cache_full_path(v)) for k, v in upstream_analysis_caches.itermappings() ])

    if len(analysis_map) > 0:
      args.extend([ '-analysis-map', ','.join(['%s:%s' % kv for kv in analysis_map.items()]) ])

    args.extend([
      '-analysis-cache', analysis_cache,
      '-classpath', ':'.join(self._zinc_classpath + classpath),
      '-output-products', depfile,
      '-mirror-analysis',
      '-d', output_dir
    ])
    args.extend(sources)
    return self.run_zinc(args)

  # Run zinc in analysis manipulation mode.
  def run_zinc_analysis(self, cache, args):
    zinc_analysis_args = [
      '-analysis',
      '-cache', cache,
      ]
    zinc_analysis_args.extend(args)
    return self.run_zinc(args=zinc_analysis_args)

  # src_cache - split this analysis cache.
  # splits - a list of (sources, dst_cache), where sources is a list of the sources whose analysis
  #          should be split into dst_cache.
  def run_zinc_split(self, src_cache, splits):
    zinc_split_args = [
      '-split', ','.join(['{%s}:%s' % (':'.join(x[0]), x[1]) for x in splits]),
      ]
    return self.run_zinc_analysis(cache=src_cache, args=zinc_split_args)

  # src_caches - a list of caches to merge into dst_cache.
  def run_zinc_merge(self, src_caches, dst_cache):
    zinc_merge_args = [
      '-merge', ':'.join(src_caches),
      ]
    return self.run_zinc_analysis(cache=dst_cache, args=zinc_merge_args)

  # cache - the analysis cache to rebase.
  # rebasings - a list of pairs (rebase_from, rebase_to). Behavior is undefined if any rebase_from
  # is a prefix of any other, as there is no guarantee that rebasings are applied in a particular order.
  def run_zinc_rebase(self, cache, rebasings):
    zinc_rebase_args = [
      '-rebase', ','.join(['%s:%s' % rebasing for rebasing in rebasings]),
      ]
    return self.run_zinc_analysis(cache=cache, args=zinc_rebase_args)

  IVY_HOME_PLACEHOLDER = '/IVY_HOME_PLACEHOLDER'
  PANTS_HOME_PLACEHOLDER = '/PANTS_HOME_PLACEHOLDER'

  def relativize_analysis_cache(self, src, dst):
    # Make an analysis cache portable. Work on a tmpfile, for safety.
    #
    # NOTE: We can't port references to deps on the Java home. This is because different JVM
    # implementations on different systems have different structures, and there's not
    # necessarily a 1-1 mapping between Java jars on different systems. Instead we simply
    # drop those references from the analysis cache.
    #
    # In practice the JVM changes rarely, and it should be fine to require a full rebuild
    # in those rare cases.
    with temporary_file_path() as tmp_analysis_cache:
      shutil.copy(src, tmp_analysis_cache)
      rebasings = [
        (self._java_home, ''),  # Erase java deps.
        (self._ivy_home, ZincUtils.IVY_HOME_PLACEHOLDER),
        (self._pants_home, ZincUtils.PANTS_HOME_PLACEHOLDER),
      ]
      exit_code = self.run_zinc_rebase(cache=tmp_analysis_cache, rebasings=rebasings)
      if not exit_code:
        shutil.copy(tmp_analysis_cache, dst)
      return exit_code

  def localize_analysis_cache(self, src, dst):
    with temporary_file_path() as tmp_analysis_cache:
      shutil.copy(src, tmp_analysis_cache)
      rebasings = [
        (ZincUtils.IVY_HOME_PLACEHOLDER, self._ivy_home),
        (ZincUtils.PANTS_HOME_PLACEHOLDER, self._pants_home),
      ]
      exit_code = self.run_zinc_rebase(cache=tmp_analysis_cache, rebasings=rebasings)
      if not exit_code:
        shutil.copy(tmp_analysis_cache, dst)
      return exit_code

  def write_plugin_info(self, resources_dir, target):
    basedir = os.path.join(resources_dir, target.id)
    with safe_open(os.path.join(basedir, _PLUGIN_INFO_FILE), 'w') as f:
      f.write(textwrap.dedent('''
        <plugin>
          <name>%s</name>
          <classname>%s</classname>
        </plugin>
      ''' % (target.plugin, target.classname)).strip())
    return basedir

  # These are the names of the various jars zinc needs. They are, conveniently and non-coincidentally,
  # the names of the flags used to pass the jar locations to zinc.
  compiler_jar_names = [ 'scala-library', 'scala-compiler' ]  # Compiler version.
  zinc_jar_names = [ 'compiler-interface', 'sbt-interface' ]  # Other jars zinc needs to be pointed to.

  @staticmethod
  def identify_zinc_jars(compiler_classpath, zinc_classpath):
    """Find the named jars in the compiler and zinc classpaths.

    TODO: When profiles migrate to regular pants jar() deps instead of ivy.xml files we can make these
          mappings explicit instead of deriving them by jar name heuristics.
    """
    ret = OrderedDict()
    ret.update(ZincUtils.identify_jars(ZincUtils.compiler_jar_names, compiler_classpath))
    ret.update(ZincUtils.identify_jars(ZincUtils.zinc_jar_names, zinc_classpath))
    return ret

  @staticmethod
  def identify_jars(names, jars):
    jars_by_name = {}
    jars_and_filenames = [(x, os.path.basename(x)) for x in jars]

    for name in names:
      jar_for_name = None
      for jar, filename in jars_and_filenames:
        if filename.startswith(name):
          jar_for_name = jar
          break
      if jar_for_name is None:
        raise TaskError('Couldn\'t find jar named %s' % name)
      else:
        jars_by_name[name] = jar_for_name
    return jars_by_name

  def find_plugins(self, plugin_names):
    """Returns a map from plugin name to plugin jar."""
    plugin_names = set(plugin_names)
    plugins = {}
    # plugin_jars is the universe of all possible plugins and their transitive deps.
    # Here we select the ones to actually use.
    for jar in self._plugin_jars:
      with open_jar(jar, 'r') as jarfile:
        try:
          with closing(jarfile.open(_PLUGIN_INFO_FILE, 'r')) as plugin_info_file:
            plugin_info = ElementTree.parse(plugin_info_file).getroot()
          if plugin_info.tag != 'plugin':
            raise TaskError, 'File %s in %s is not a valid scalac plugin descriptor' % (_PLUGIN_INFO_FILE, jar)
          name = plugin_info.find('name').text
          if name in plugin_names:
            if name in plugins:
              raise TaskError, 'Plugin %s defined in %s and in %s' % (name, plugins[name], jar)
            # It's important to use relative paths, as the compiler flags get embedded in the zinc
            # analysis file, and we port those between systems via the artifact cache.
            plugins[name] = os.path.relpath(jar, self._pants_home)
        except KeyError:
          pass

    unresolved_plugins = plugin_names - set(plugins.keys())
    if len(unresolved_plugins) > 0:
      raise TaskError, 'Could not find requested plugins: %s' % list(unresolved_plugins)
    return plugins
