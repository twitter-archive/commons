from __future__ import print_function

import os
import time

from twitter.common.dirutil import touch
from twitter.common.python.fetcher import Fetcher, PyPIFetcher
from twitter.common.python.http import Crawler
from twitter.common.python.obtainer import Obtainer, ObtainerFactory
from twitter.common.python.interpreter import PythonInterpreter
from twitter.common.python.package import distribution_compatible
from twitter.common.python.platforms import Platform
from twitter.common.python.resolver import resolve, requirement_is_exact
from twitter.common.python.translator import (
    ChainedTranslator,
    EggTranslator,
    SourceTranslator,
    Translator
)

from .python_setup import PythonSetup

from pkg_resources import (
    Environment,
    WorkingSet,
)


def get_platforms(platform_list):
  def translate(platform):
    return Platform.current() if platform == 'current' else platform
  return tuple(set(map(translate, platform_list)))


def fetchers_from_config(config):
  fetchers = []
  fetchers.extend(Fetcher([url]) for url in config.getlist('python-repos', 'repos', []))
  fetchers.extend(PyPIFetcher(url) for url in config.getlist('python-repos', 'indices', []))
  return fetchers


def crawler_from_config(config, conn_timeout=None):
  download_cache = PythonSetup(config).scratch_dir('download_cache', default_name='downloads')
  return Crawler(cache=download_cache, conn_timeout=conn_timeout)


class PantsEnvironment(Environment):
  def __init__(self, interpreter, platform=None):
    platform = platform or Platform.current()
    self.__interpreter = interpreter
    super(PantsEnvironment, self).__init__(
        search_path=[], python=interpreter.python, platform=platform)

  def can_add(self, dist):
    return distribution_compatible(dist, self.__interpreter, platform=self.platform)


def resolve_multi(config,
                  requirements,
                  interpreter=None,
                  platforms=None,
                  conn_timeout=None,
                  ttl=3600):
  """Multi-platform dependency resolution for PEX files.

     Given a pants configuration and a set of requirements, return a list of distributions
     that must be included in order to satisfy them.  That may involve distributions for
     multiple platforms.

     :param config: Pants :class:`Config` object.
     :param requirements: A list of :class:`PythonRequirement` objects to resolve.
     :param interpreter: :class:`PythonInterpreter` for which requirements should be resolved.
                         If None specified, defaults to current interpreter.
     :param platforms: Optional list of platforms against requirements will be resolved. If
                         None specified, the defaults from `config` will be used.
     :param conn_timeout: Optional connection timeout for any remote fetching.
     :param ttl: Time in seconds before we consider re-resolving an open-ended requirement, e.g.
                 "flask>=0.2" if a matching distribution is available on disk.  Defaults
                 to 3600.
  """
  class PantsObtainerFactory(ObtainerFactory):
    def __init__(self, platform, interpreter):
      self.translator = Translator.default(install_cache=install_cache,
                                           interpreter=interpreter,
                                           platform=platform)
      self._crawler = crawler_from_config(config, conn_timeout=conn_timeout)
      self._default_obtainer = Obtainer(self._crawler,
                                        fetchers_from_config(config) or [PyPIFetcher()],
                                        self.translator)

    def __call__(self, requirement):
      if hasattr(requirement, 'repository') and requirement.repository:
        obtainer = Obtainer(crawler=self._crawler,
                            fetchers=[Fetcher([requirement.repository])],
                            translators=self.translator)
      else:
        obtainer = self._default_obtainer
      return obtainer

  distributions = dict()
  interpreter = interpreter or PythonInterpreter.get()
  if not isinstance(interpreter, PythonInterpreter):
    raise TypeError('Expected interpreter to be a PythonInterpreter, got %s' % type(interpreter))

  install_cache = PythonSetup(config).scratch_dir('install_cache', default_name='eggs')
  platforms = get_platforms(platforms or config.getlist('python-setup', 'platforms', ['current']))
  for platform in platforms:
    distributions[platform] = resolve(requirements=requirements,
                                      obtainer_factory=PantsObtainerFactory(
                                        platform=platform,
                                        interpreter=interpreter),
                                      interpreter=interpreter,
                                      platform=platform)
  return distributions
