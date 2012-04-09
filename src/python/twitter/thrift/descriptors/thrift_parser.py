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

import json
import sys

import antlr3
from antlrgen.twitter.thrift.descriptors.AntlrThriftLexer import AntlrThriftLexer
from antlrgen.twitter.thrift.descriptors.AntlrThriftParser import AntlrThriftParser
from antlrgen.twitter.thrift.descriptors.AntlrThriftTreeWalker import AntlrThriftTreeWalker

from twitter.thrift.text import thrift_json_encoder
from twitter.thrift.descriptors.thrift_parser_error import ThriftParserError

class ThriftParser(object):
  """Parses a thrift file and creates descriptors for all the entities in that file.

  Each of the parse methods below returns an instance of thrift_descriptors.Program."""
  def __init__(self):
    pass

  def parse_string(self, data):
    """Parse from a string."""
    return self._parse(antlr3.ANTLRStringStream(data))

  def parse_file(self, path):
    """Parse from a file specififed by path."""
    return self._parse(antlr3.ANTLRFileStream(path))

  def parse_input(self, input):
    """Parse from a file-like object."""
    return self._parse(antlr3.ANTLRInputStream(input))

  def _parse(self, char_stream):
    # Parse the raw input to an AST.
    lexer = AntlrThriftLexer(char_stream)
    tokens = antlr3.CommonTokenStream(lexer)
    parser = AntlrThriftParser(tokens)
    root = parser.program().tree
    if parser.getNumberOfSyntaxErrors() > 0:
      raise ThriftParserError('Thrift parse failed')

    # Walk the AST.
    nodes = antlr3.tree.CommonTreeNodeStream(root)
    nodes.setTokenStream(tokens)
    walker = AntlrThriftTreeWalker(nodes)
    return walker.program()

