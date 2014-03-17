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
from .translator import (
    ChainedTranslator,
    EggTranslator,
    Translator,
)

from pkg_resources import (
    Environment,
    WorkingSet,
)


class ResolutionError(Exception):
  pass


# Some examples
#
# iteration:
#    reqs, stack = stack, []
#    save distribution lists
#    filter existing distribution lists against reqs
#    if any req causes filter to go to zero:
#       restore distribution lists
#       return False
#
#
# reqs
#   setuptools [setuptools>=1, setuptools==2.1.2]
#
# reqs:
#
#   celery
#     - pytz>dev
#     - billiard>=3.3.0.14,<3.4
#     - kombu>=3.0.12,<4.0
#         - anyjson>=0.3.3
#         - amqp>=1.4.4,<2
#         - importlib
#         - ordereddict
#
#   flask
#     - werkzeug>=0.7
#     - Jinja2>=2.4
#        - markupsafe
#     - itsdangerous>=0.21
#
#   gevent
#     - greenlet
#
#   tornado
#     - backports.ssl-match-hostname
#
#   twisted
#     - zope.interface>=3.6.0
#       - setuptools
#
#   python-openstackclient
#     - pbr>=0.6,<1.0
#       - pip>=1.0
#     - cliff>=1.5.2
#       - argparse
#       - cmd2>=0.6.7
#       - PrettyTable>=0.6,<0.8
#       - pyparsing>=2.0.1
#       - stevedore
#     - keyring>=1.6.1,<2.0,>=2.1
#     - pycrypto>=2.6
#     - python-glanceclient>=0.9.0
#       - pyOpenSSL
#         - cryptography>=0.2.1
#           - cffi>=0.8
#             - pycparser
#           - six>=1.4.1
#       - warlock>=1.0.1,<2
#     - python-keystoneclient>=0.6.0
#       - Babel>=1.3
#       - iso8601>=0.1.8
#       - netadd>=0.7.6
#       - oslo.config>=1.2.0
#     - python-novaclient>=2.15.0
#       - simplejson>=2.0.9
#     - python-cinderclient>=1.0.6
#       - pbr>=0.5.21,<1.0
#         - pip>=1.0
#       - argparse
#       - PrettyTable>=0.7,<0.8
#       - requests>=1.1
#       - simplejson>=2.0.9
#       - Babel>=1.3
#         - pytz>=0a
#       - six>=1.4.1
#     - requests>=1.1
#     - six>=1.4.1
#
#
# We could model this as a strict CSP or MAX CSP problem, but this could take
# a nearly unbounded amount of time to satisfy.
#
# distributionset = {
#    <key> => {list of packages}
# }
#
# node = root = target/package
#
# packages(requirement, obtainer, existing=None) -> list of packages that satisfy requirement
#                                                -> if existing is None, use obtainer
#                                                -> if existing is not None, only select from existing
#
# requires(target_or_package, requirement) -> list of requirements from target/package
#
# node [distributionset]
#   - r1
#   - r2
#   - r3
#
# for each requirement under node:
#   distributionset[requirement.key] = packages(requirement, obtainer, distributionset.get(requirement.key))
#
# if any(value == [] for value in distributionset.values()):
#   return UNSATISFIABLE
#
# for requirement under node:
#   for package in distributionset[requirement.key]:
#     package_requirements = requires(package, requirement)  # fetches package & creates distribution
#     if descend(distributionset.copy(), package_requirements) != UNSATISFIABLE:
#       break
#
#


class Untranslateable(Exception):
  pass


class Unsatisfiable(Exception):
  pass


def packages(requirement, obtainer, interpreter, platform, existing=None):
  if existing is None:
    existing = list(obtainer.iter(requirement))

  existing = [package for package in existing
              if package.satisfies(requirement)
              and package.compatible(interpreter.identity, platform)]

  return existing


def requires(package, obtainer, requirement):
  dist = obtainer.translator.translate(package)
  if dist is None:
    raise Untranslateable('Package %s is not translateable.' % package)
  return dist.requires(extras=requirement.extras)


"""

from twitter.common.python.http.crawler import Crawler
from twitter.common.python.fetcher import PyPIFetcher
from twitter.common.python.obtainer import Obtainer
from twitter.common.python.translator import Translator
from twitter.common.python.interpreter import PythonInterpreter
from twitter.common.python.platforms import Platform
from twitter.common.python.resolver import really_resolve

import pkg_resources
p = pkg_resources.Requirement.parse
streqs = [p('twitter.common.python==0.4.3'), p('setuptools==2.1.2')]

interpreter = PythonInterpreter.get().with_extra(
  'wheel', '0.22.0', '/Users/wickman/clients/twitter-commons/.pants.d/python/interpreters/CPython-2.6.9/wheel-0.22.0-py2.6.egg'
)
platform = Platform.current()

obtainer = Obtainer(Crawler(), [PyPIFetcher()], [Translator.default(interpreter=interpreter, platform=platform)])


really_resolve(streqs, obtainer, interpreter, platform)

"""


def really_resolve(requirements, obtainer, interpreter, platform):
  requirements = list(requirements)
  distribution_set = defaultdict(list)
  requirement_set = defaultdict(list)
  processed_requirements = set()

  while True:

    # flatten existing requirements
    while requirements:
      requirement = requirements.pop(0)

      print('Resolving requirement: %s' % requirement)
      requirement_set[requirement.key].append(requirement)
      distribution_list = distribution_set[requirement.key] = packages(
          requirement,
          obtainer,
          interpreter,
          platform,
          existing=distribution_set.get(requirement.key))
      print('Distribution list now:')
      for distribution in distribution_list:
        print('  %s' % distribution)
      if not distribution_list:
        print('  None')
        raise Unsatisfiable('Cannot satisfy requirement list.')

    # get their dependencies
    for requirement_key, requirement_list in requirement_set.items():
      print('Requirement key: %s, requirement list: %s' % (requirement_key, requirement_list))
      new_requirements = OrderedSet()
      latest_package = distribution_set[requirement_key][0]
      for requirement in requirement_list:
        if requirement in processed_requirements:
          continue
        new_requirements.update(requires(latest_package, obtainer, requirement))
        processed_requirements.add(requirement)
      print('Additional requirements:')
      for requirement in new_requirements:
        print('  %s' % requirement)
      if not new_requirements:
        print('  None')
      requirements.extend(list(new_requirements))

    if not requirements:
      break

  return distribution_set



class ResolverEnvironment(Environment):
  def __init__(self, interpreter, *args, **kw):
    kw['python'] = interpreter.python
    self.__interpreter = interpreter
    super(ResolverEnvironment, self).__init__(*args, **kw)

  def can_add(self, dist):
    return distribution_compatible(dist, self.__interpreter, platform=self.platform)


def requirement_is_exact(req):
  return req.specs and len(req.specs) == 1 and req.specs[0][0] == '=='


def resolve(requirements,
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
