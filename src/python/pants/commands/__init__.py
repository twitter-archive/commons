# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

class Command(object):
  "Baseclass for all pants subcommands."

  @staticmethod
  def find_command_class(name):
    """Attempts to find the command class for the given command name.  Raises a KeyError if the
    command could not be found.

    name: the case insensitive name of the command"""

    try:
      command_class = getattr(__import__(__name__), name.capitalize())
    except AttributeError as e:
      raise KeyError("Failed to find a command with name %s in the commands module: %s" % (name, e))

    if not issubclass(command_class, Command):
      raise KeyError("Invalid command class %s, must be a subclass of Command" % command_class)

    return command_class

  def __init__(self, root_dir, parser, argv):
    """root_dir: The root directory of the pants workspace
    parser: an OptionParser
    argv: the subcommand arguments to parse"""

    object.__init__(self)

    self.root_dir = root_dir
    self.setup_parser(parser)

    # Override the OptionParser's error with more useful output
    def error(message = None):
      if message:
          print message
          print
      parser.print_help()
      parser.exit(status = 1)
    parser.error = error

    self.options, self.args = parser.parse_args(argv)
    self.parser = parser

  def setup_parser(self, parser):
    """Subclasses should override and confiure the OptionParser to reflect the subcommand option
    and argument requirements.  Upon successful construction, subcommands will be able to access
    self.options and self.args."""

    pass

  def error(self, message = None):
    self.parser.error(message)

  def execute(self):
    """Subcommands should override to perform the command action.  The value returned should be an
    int, 0 indicating success and any other value indicating failure."""

    pass

from build import Build
from depmap import Depmap
from doc import Doc
from filemap import Filemap
from files import Files
from help import Help
from list import List

__all__ = (
  Build,
  Depmap,
  Doc,
  Filemap,
  Files,
  Help,
  List,
)
