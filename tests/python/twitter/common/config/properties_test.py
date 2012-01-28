# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

__author__ = 'John Sirois'

import unittest

from cStringIO import StringIO

from twitter.common.collections import OrderedDict
from twitter.common.config import Properties
from twitter.common.contextutil import temporary_file

class PropertiesTest(unittest.TestCase):
  def test_empty(self):
    self.assertLoaded('', {})
    self.assertLoaded(' ', {})
    self.assertLoaded('\t', {})
    self.assertLoaded('''

    ''', {})

  def test_comments(self):
    self.assertLoaded('''
# not=a prop
a=prop
 ! more non prop
    ''', {'a': 'prop'})

  def test_kv_sep(self):
    self.assertLoaded('''
    a=b
    c   d\=
    e\: :f
    jack spratt = \tbob barker
    g
    h=
    i :
    ''', {'a': 'b', 'c': 'd=', 'e:': 'f', 'jack spratt': 'bob barker', 'g': '', 'h': '', 'i': ''})

  def test_line_continuation(self):
    self.assertLoaded('''
    # A 3 line continuation
    a\\\\
        \\
           \\b
    c=\
    d
    e: \
    f
    g\
    :h
    i\
    = j
    ''', {'a\\': '\\b', 'c': 'd', 'e': 'f', 'g': 'h', 'i': 'j'})

  def test_stream(self):
    with temporary_file() as props_out:
      props_out.write('''
      it's a = file
      ''')
      props_out.close()
      with open(props_out.name, 'r') as props_in:
        self.assertLoaded(props_in, {'it\'s a': 'file'})

  def assertLoaded(self, contents, expected):
    self.assertEquals(expected, Properties.load(contents))

  def test_dump(self):
    props = OrderedDict()
    props['a'] = 1
    props['b'] = '''2
'''
    props['c'] =' 3 : ='
    out = StringIO()
    Properties.dump(props, out)
    print out.getvalue()
    self.assertEquals('a=1\nb=2\\\n\nc=\\ 3\\ \\:\\ \\=\n', out.getvalue())
