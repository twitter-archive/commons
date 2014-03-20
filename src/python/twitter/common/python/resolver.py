from __future__ import print_function

from collections import defaultdict

from .base import maybe_requirement_list
from .fetcher import Fetcher, PyPIFetcher
from .http import Crawler
from .interpreter import PythonInterpreter
from .obtainer import Obtainer
from .orderedset import OrderedSet
from .package import distribution_compatible
from .platforms import Platform
from .translator import EggTranslator, Translator

from pkg_resources import Environment, WorkingSet


class Untranslateable(Exception): pass

class Unsatisfiable(Exception): pass

def resolve(requirements, obtainer_factory, interpreter, platform):
  """List all distributions needed to (recursively) meet `requirements`

  When resolving dependencies, multiple (potentially incompatible) requirements may be encountered.
  Handle this situation by iteratively filtering a set of potential project
  distributions by new requirements, and finally choosing the highest version meeting all
  requirements, or raise an error indicating unsatisfiable requirements.

  Note: should `pkg_resources.WorkingSet.resolve` correctly handle multiple requirements in the
  future this should go away in favor of using what setuptools provides.
  """
  requirements = list(requirements)
  distribution_set = defaultdict(list)
  requirement_set = defaultdict(list)
  processed_requirements = set()

  def packages(requirement, obtainer, interpreter, platform, existing=None):
    if existing is None:
      existing = obtainer.iter(requirement)
    return [package for package in existing
            if package.satisfies(requirement)
            and package.compatible(interpreter.identity, platform)]

  def requires(package, translator, requirement):
    dist = translator.translate(package)
    if dist is None:
      raise Untranslateable('Package %s is not translateable.' % package)
    return dist.requires(extras=requirement.extras)

  while True:
    while requirements:
      requirement = requirements.pop(0)
      requirement_set[requirement.key].append(requirement)
      obtainer = obtainer_factory.get(requirement)
      distribution_list = distribution_set[requirement.key] = packages(
          requirement,
          obtainer,
          interpreter,
          platform,
          existing=distribution_set.get(requirement.key))
      if not distribution_list:
        raise Unsatisfiable('Cannot satisfy requirements: %s' % requirement_set[requirement.key])

    # get their dependencies
    for requirement_key, requirement_list in requirement_set.items():
      new_requirements = OrderedSet()
      latest_package = distribution_set[requirement_key][0]
      for requirement in requirement_list:
        if requirement in processed_requirements:
          continue
        new_requirements.update(requires(latest_package, obtainer.translator, requirement))
        processed_requirements.add(requirement)
      requirements.extend(list(new_requirements))

    if not requirements:
      break

  to_activate = set()
  [to_activate.add(obtainer.translate_from([distributions[0]]))
   for distributions in distribution_set.values()]
  return to_activate


class ResolverEnvironment(Environment):
  def __init__(self, interpreter, *args, **kw):
    kw['python'] = interpreter.python
    self.__interpreter = interpreter
    super(ResolverEnvironment, self).__init__(*args, **kw)

  def can_add(self, dist):
    return distribution_compatible(dist, self.__interpreter, platform=self.platform)


def requirement_is_exact(req):
  return req.specs and len(req.specs) == 1 and req.specs[0][0] == '=='


def resolve_old(requirements,
            cache=None,
            crawler=None,
            fetchers=None,
            obtainer=None,
            interpreter=None,
            platform=None):
  """Resolve a list of requirements into distributions.

     :param requirements: A list of strings or :class:`pkg_resources.Requirement` objects to be
                          resolved.
     :param cache: The filesystem path to cache distributions or None for no caching.
     :param crawler: The :class:`Crawler` object to use to crawl for artifacts.  If None specified
                     a default crawler will be constructed.
     :param fetchers: A list of :class:`Fetcher` objects for generating links.  If None specified,
                      default to fetching from PyPI.
     :param obtainer: An :class:`Obtainer` object for converting from links to
                      :class:`pkg_resources.Distribution` objects.  If None specified, a default
                      will be provided that accepts eggs or building from source.
     :param interpreter: A :class:`PythonInterpreter` object to resolve against.  If None specified,
                         use the current interpreter.
     :param platform: The string representing the platform to be resolved, such as `'linux-x86_64'`
                      or `'macosx-10.7-intel'`.  If None specified, the current platform is used.
  """
  requirements = maybe_requirement_list(requirements)

  # Construct defaults
  crawler = crawler or Crawler()
  fetchers = fetchers or [PyPIFetcher()]
  interpreter = interpreter or PythonInterpreter.get()
  platform = platform or Platform.current()

  # wire up translators / obtainer
  if cache:
    shared_options = dict(install_cache=cache, platform=platform, interpreter=interpreter)
    translator = EggTranslator(**shared_options)
    cache_obtainer = Obtainer(crawler, [Fetcher([cache])], translator)
  else:
    cache_obtainer = None

  if not obtainer:
    translator = Translator.default(install_cache=cache, platform=platform, interpreter=interpreter)
    obtainer = Obtainer(crawler, fetchers, translator)

  # make installer
  def installer(req):
    if cache_obtainer and requirement_is_exact(req):
      dist = cache_obtainer.obtain(req)
      if dist:
        return dist
    return obtainer.obtain(req)

  # resolve
  working_set = WorkingSet(entries=[])
  env = ResolverEnvironment(interpreter, search_path=[], platform=platform)
  return working_set.resolve(requirements, env=env, installer=installer)
