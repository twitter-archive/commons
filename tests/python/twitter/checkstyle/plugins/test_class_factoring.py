from twitter.checkstyle.common import Nit, PythonFile
from twitter.checkstyle.plugins.class_factoring import ClassFactoring


BAD_CLASS = PythonFile.from_statement("""
class Distiller(object):
  CONSTANT = "foo"

  def foo(self, value):
    return os.path.join(Distiller.CONSTANT, value)
""")


def test_class_factoring():
  plugin = ClassFactoring(BAD_CLASS)
  nits = list(plugin.nits())
  assert len(nits) == 1
  assert nits[0].code == 'T800'
  assert nits[0].severity == Nit.WARNING
