from twitter.checkstyle.common import Nit, PythonFile
from twitter.checkstyle.plugins.indentation import Indentation


def test_indentation():
  ind = Indentation(PythonFile.from_statement("""
    def foo():
        pass
  """))
  nits = list(ind.nits())
  assert len(nits) == 1
  assert nits[0].code == 'T100'
  assert nits[0].severity == Nit.ERROR

  ind = Indentation(PythonFile.from_statement("""
    def foo():
      pass
  """))
  nits = list(ind.nits())
  assert len(nits) == 0

  ind = Indentation(PythonFile.from_statement("""
    def foo():
      baz = (
          "this "
          "is "
          "ok")
  """))
  nits = list(ind.nits())
  assert len(nits) == 0
