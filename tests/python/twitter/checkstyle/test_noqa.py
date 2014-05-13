from twitter.checkstyle.checker import (
    apply_filter,
    noqa_file_filter,
    noqa_line_filter,
)
from twitter.checkstyle.common import CheckstylePlugin, PythonFile


class Rage(CheckstylePlugin):
  def nits(self):
    for line_no, _ in self.python_file.enumerate():
      yield self.error('T999', 'I hate everything!', line_no)


def test_noqa_line_filter():
  nits = apply_filter(PythonFile.from_statement("""
    print('This is not fine')
    print('This is fine')  # noqa
  """), Rage, None)
  
  nits = list(nits)
  assert len(nits) == 1, ('Actually got nits: %s' % (' '.join('%s:%s' % (nit._line_number, nit) for nit in nits)))
  assert nits[0].code == 'T999'


def test_noqa_file_filter():
  nits = apply_filter(PythonFile.from_statement("""
    # checkstyle: noqa
    print('This is not fine')
    print('This is fine')
  """), Rage, None)
  
  nits = list(nits)
  assert len(nits) == 0
