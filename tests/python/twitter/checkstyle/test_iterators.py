from StringIO import StringIO
from textwrap import dedent

from twitter.checkstyle.iterators import diff_lines


class Blob(object):
  def __init__(self, blob):
    self._blob = blob
    self.hexsha = 'ignore me'

  @property
  def data_stream(self):
    return StringIO(self._blob)


def make_blob(stmt):
  return Blob(dedent('\n'.join(stmt.splitlines()[1:])))


def test_diff_lines():
  blob_a = make_blob("""
    001 herp derp
  """)

  assert list(diff_lines(blob_a, blob_a)) == []

  blob_b = make_blob("""
    001 derp herp
  """)

  assert list(diff_lines(blob_a, blob_b)) == [1]

  blob_c = make_blob("""
    001 herp derp
    002 derp derp
  """)

  assert list(diff_lines(blob_a, blob_c)) == [2]
  assert list(diff_lines(blob_c, blob_a)) == []

  blob_d = make_blob("""
    001
    002
    003
    004
  """)

  blob_e = make_blob("""
    001
    004
  """)

  assert list(diff_lines(blob_d, blob_e)) == []
  assert list(diff_lines(blob_e, blob_d)) == [2, 3]

  blob_f = make_blob("""
    001
    002
    003
    004
  """)

  blob_g = make_blob("""
    002
    001
    004
    003
  """)

  assert list(diff_lines(blob_f, blob_g)) == [1, 3]
  assert list(diff_lines(blob_g, blob_f)) == [1, 3]
