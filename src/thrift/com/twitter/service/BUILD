from __future__ import print_function

import atexit
import os
import sys
import re
import urllib2

from twitter.common.dirutil import Fileset, safe_delete


class RemotePythonThrift(object):
  # TODO(wickman) Use the antlr thrift parser to just walk the thrift AST
  # and replace keywords named by 'from' with 'from_'.
  FROM_RE = re.compile('from[,;]*$', re.MULTILINE)
  FROM_REPLACEMENT = 'from_'

  def __init__(self, http_base):
    self._base = http_base
    self._staging_dir = os.path.dirname(__file__)
    self._fetched = []

  def _fetch(self, sources):
    for source in sources:
      if isinstance(source, tuple):
        assert len(source) == 2, 'Expected source, namespace tuple, got %s' % repr(source)
        source_file, namespace = source
      elif isinstance(source, str):
        source_file, namespace = source, None
      fetch_path = self._base + '/' + source_file
      print('Fetching %s' % fetch_path, file=sys.stderr)
      target_file = os.path.join(self._staging_dir, source_file)
      url = urllib2.urlopen(fetch_path)
      with open(target_file, 'wb') as fp:
        fp.write(self.prefilter(url.read(), namespace=namespace))
      self._fetched.append(target_file)
      yield source_file

  def prefilter(self, content, namespace=None):
    return ''.join(['namespace py %s\n' % namespace if namespace else '',
      re.sub(RemotePythonThrift.FROM_RE, RemotePythonThrift.FROM_REPLACEMENT, content)])

  def cleanup(self):
    for fetched in self._fetched:
      safe_delete(fetched)

  def python_thrift_library(self, name, sources=None, **kw):
    def fetch():
      atexit.register(self.cleanup)
      return self._fetch(sources)
    python_thrift_library(name=name, sources=Fileset(fetch), **kw)


finagle = RemotePythonThrift(
  "https://raw.github.com/twitter/finagle/master/finagle-thrift/src/main/thrift")


finagle.python_thrift_library(
  name = 'finagle-py-thrift',
  sources = [
    ('tracing.thrift', 'gen.twitter.finagle.thrift'),
  ])
