from twitter.checkstyle.common import Nit, PythonFile
from twitter.checkstyle.plugins.print_statements import PrintStatements


def test_print_statements():
  ps = PrintStatements(PythonFile.from_statement("""
    from __future__ import print_function
    print("I do what I want")
    
    class Foo(object):
      def print(self):
        "I can do this because it's not a reserved word."
  """))
  assert len(list(ps.nits())) == 0

  ps = PrintStatements(PythonFile.from_statement("""
    print("I do what I want")
  """))
  assert len(list(ps.nits())) == 0

  ps = PrintStatements(PythonFile.from_statement("""
    print["I do what I want"]
  """))
  nits = list(ps.nits())
  assert len(nits) == 1
  assert nits[0].code == 'T607'
  assert nits[0].severity == Nit.ERROR
