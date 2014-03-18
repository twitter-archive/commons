from twitter.common.python.pep425 import PEP425, PEP425Extras
from twitter.common.python.interpreter import PythonIdentity

import pytest


def test_platform_iterator():
  # non macosx
  assert list(PEP425Extras.platform_iterator('blah')) == ['blah']
  assert list(PEP425Extras.platform_iterator('linux_x86_64')) == ['linux_x86_64']

  # macosx
  assert set(PEP425Extras.platform_iterator('macosx_10_4_x86_64')) == set([
      'macosx_10_4_x86_64',
      'macosx_10_3_x86_64',
      'macosx_10_2_x86_64',
      'macosx_10_1_x86_64',
      'macosx_10_0_x86_64',
  ])
  assert set(PEP425Extras.platform_iterator('macosx_10_0_universal')) == set([
      'macosx_10_0_i386',
      'macosx_10_0_ppc',
      'macosx_10_0_ppc64',
      'macosx_10_0_x86_64',
      'macosx_10_0_universal',
  ])

  with pytest.raises(ValueError):
    list(PEP425Extras.platform_iterator('macosx_10'))

  with pytest.raises(ValueError):
    list(PEP425Extras.platform_iterator('macosx_10_0'))

  with pytest.raises(ValueError):
    list(PEP425Extras.platform_iterator('macosx_9_x86_64'))


def test_iter_supported_tags():
  identity = PythonIdentity('CPython', 2, 6, 5)
  platform = 'linux-x86_64'

  def iter_solutions():
    for interp in ('cp', 'py'):
      for interp_suffix in ('2', '20', '21', '22', '23', '24', '25', '26'):
        for platform in ('linux_x86_64', 'any'):
          yield (interp + interp_suffix, 'none', platform)

  assert set(PEP425.iter_supported_tags(identity, platform)) == set(iter_solutions())
