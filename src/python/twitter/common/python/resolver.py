from __future__ import print_function

from collections import defaultdict

from .orderedset import OrderedSet
from .package import distribution_compatible

from pkg_resources import Environment


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
