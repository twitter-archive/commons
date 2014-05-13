from twitter.checkstyle.common import Nit, PythonFile
from twitter.checkstyle.plugins.missing_contextmanager import MissingContextManager


def test_missing_contextmanager():
  mcm = MissingContextManager(PythonFile.from_statement("""
    with open("derp.txt"):
      pass
    
    with open("herp.txt") as fp:
      fp.read()
  """))
  nits = list(mcm.nits())
  assert len(nits) == 0

  mcm = MissingContextManager(PythonFile.from_statement("""
    foo = open("derp.txt")
  """))
  nits = list(mcm.nits())
  assert len(nits) == 1
  assert nits[0].code == 'T802'
  assert nits[0].severity == Nit.WARNING

  # TODO(wickman) In these cases suggest using contextlib.closing
  mcm = MissingContextManager(PythonFile.from_statement("""
    from urllib2 import urlopen
    the_googs = urlopen("http://www.google.com").read()
  """))
  nits = list(mcm.nits())
  assert len(nits) == 0
