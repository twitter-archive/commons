from twitter.checkstyle.common import Nit, PythonFile
from twitter.checkstyle.plugins.except_statements import ExceptStatements


EXCEPT_TEMPLATE = """
try:                  # 001
  1 / 0
%s
  pass
"""


def nits_from(clause):
  return list(ExceptStatements(PythonFile.from_statement(EXCEPT_TEMPLATE % clause)).nits())


def test_except_statements():
  for clause in ('except:', 'except :', 'except\t:'):
    nits = nits_from(clause)
    assert len(nits) == 1
    assert nits[0].code == 'T803'
    assert nits[0].severity == Nit.ERROR

  for clause in (
      'except KeyError, e:',
      'except (KeyError, ValueError), e:',
      'except KeyError, e :',
      'except (KeyError, ValueError), e\t:'):
    nits = nits_from(clause)
    assert len(nits) == 1
    assert nits[0].code == 'T601'
    assert nits[0].severity == Nit.ERROR

  for clause in (
      'except KeyError:',
      'except KeyError as e:',
      'except (KeyError, ValueError) as e:',
      'except (KeyError, ValueError) as e:'):
    nits = nits_from(clause)
    assert len(nits) == 0
