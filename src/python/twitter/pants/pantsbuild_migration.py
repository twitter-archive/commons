# A one-time script to migrate twitter/commons pants code to pantsbuild/pants.

from __future__ import (nested_scopes, generators, division, absolute_import, with_statement,
                        print_function, unicode_literals)

import os
import re
import sys


PANTS_ROOT = os.path.dirname(os.path.realpath(__file__))
SRC_ROOT = os.path.dirname(os.path.dirname(PANTS_ROOT))

IMPORT_RE = re.compile(r'(from .* import .*)|import .*')

HEADER_COMMENT = [
  '# Copyright Pants, Inc. See LICENSE file license details.'
]

FUTURE_IMPORTS = [
  'from __future__ import (nested_scopes, generators, division, absolute_import, with_statement,',
  '                        print_function, unicode_literals)'
]

class PantsSourceFile(object):
  def __init__(self, path):
    self._path = path
    self._package = os.path.relpath(os.path.dirname(os.path.abspath(path)), SRC_ROOT).replace(os.path.sep, '.')
    self._old_lines = []
    self._imports = []
    self._body = []

  def process(self):
    self.load()
    self.rewrite_header()
    self.save()

  def load(self):
    with open(self._path, 'r') as infile:
      self._old_lines = [line.rstrip() for line in infile.read().splitlines()]

  def rewrite_header(self):
    # Find first non-header-comment line.
    p = next(i for i, line in enumerate(self._old_lines) if line and not line.startswith('#'))
    content_lines = self._old_lines[p:]
    # Find first non-import line.
    q = next(i for i, line in enumerate(content_lines) if line and not IMPORT_RE.match(line))
    old_imports = filter(lambda x: not x.startswith('from __future__ import'), content_lines[0:q])
    self._imports = self.process_imports(old_imports)
    self._body = content_lines[q:]

  def process_imports(self, imports):
    def absify_import(imp):
      if imp.startswith('from .'):
        return 'from %s.' % self._package + imp[6:]
      else:
        return imp
    abs_imports = map(absify_import, imports)
    return sorted(filter(None, abs_imports))

  def save(self):
    with open(self._path, 'w') as outfile:
      for lines in [HEADER_COMMENT, FUTURE_IMPORTS, self._imports, self._body]:
        for line in lines:
          outfile.write(line)
          outfile.write('\n')
        outfile.write('\n')


if __name__ == '__main__':
  path = sys.argv[1]
  print('PROCESSING: %s' % path)
  srcfile = PantsSourceFile(path)
  srcfile.process()
