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

from commands import Command
from pants import Address

import ConfigParser
import commands
import inspect
import optparse
import os
import sys
import traceback

_HELP_ALIASES = set([
  '-h',
  '--help',
  'help',
])

_BUILD_COMMAND = 'build'

# Support legacy pants invocation syntax when the only subcommand was Build and the spec was
# supplied as an option instead of an argument
_BUILD_ALIASES = set([
  '-s',
  '--spec',
  '-f',
])

_VERSION = '0.0.2'
_DISABLE_PANTS_RC_OPTION = '--no-pantsrc'

def find_all_commands():
  def is_command(cls):
    return inspect.isclass(cls) and issubclass(cls, Command) and cls != Command
  for name, cls in inspect.getmembers(commands):
    if is_command(cls):
      yield '%s\t%s' % (name.lower(), cls.__doc__)

def _help(root_dir):
  print 'Pants %s @ BUILD_ROOT: %s' % (_VERSION, root_dir)
  print
  print 'Available subcommands:\n\t%s' % '\n\t'.join(find_all_commands())
  print
  print """Default subcommand flags can be stored in ~/.pantsrc using the 'options' key of a
section named for the subcommand in ini style format, ie:
  [build]
  options: --fast"""
  exit()

def _prepend_default_options(command, args):
  if _DISABLE_PANTS_RC_OPTION not in args:
    pantsrc = os.path.join(os.path.expanduser('~'), '.pantsrc')
    if os.path.exists(pantsrc):
      config = ConfigParser.SafeConfigParser()
      config.read(pantsrc)
      if config.has_option(command, 'options'):
        expanded_options = config.get(command, 'options').split() + args
        print "(using ~/.pantsrc expansion: pants %s %s)" % (command, ' '.join(expanded_options))
        return expanded_options

  return args

def _find_command_class_name(root_dir, args):
  arg = args[0]

  command = _BUILD_COMMAND if arg in _BUILD_ALIASES else arg
  command_class_name = command.capitalize()
  if hasattr(commands, command_class_name):
    subcommand_args = args[1:] if len(args) > 1 else []
    return _prepend_default_options(command, subcommand_args), command_class_name

  if arg.startswith('-'):
    exit('Unrecognized option: %s' % arg)

  # If a subcommand is not explicitly provided, default to Build when the 1st argument is a valid
  # BUILD target address
  try:
    Address.parse(root_dir, arg)
    return _prepend_default_options(_BUILD_COMMAND, args), _BUILD_COMMAND.capitalize()
  except:
    exit('Failed to execute pants build: %s' % traceback.format_exc())

def _find_command_class(root_dir, args):
  args, command_class_name = _find_command_class_name(root_dir, args)
  return args, Command.find_command_class(command_class_name)

def main():
  if 'BUILD_ROOT' not in os.environ:
    exit('BUILD_ROOT environment must be defined')

  root_dir = os.path.realpath(os.environ['BUILD_ROOT'])
  if not os.path.exists(root_dir):
    exit('BUILD_ROOT does not point to a valid path: %s' % root_dir)

  if len(sys.argv) < 2 or (len(sys.argv) == 2 and sys.argv[1] in _HELP_ALIASES):
    _help(root_dir)

  subcommand_args, subcommand_class = _find_command_class(root_dir, sys.argv[1:])

  parser = optparse.OptionParser(version = '%%prog %s' % _VERSION)
  parser.add_option(_DISABLE_PANTS_RC_OPTION, action = 'store_false', dest = 'pantsrc',
                    default = True, help = 'Specifies that ~/.pantsrc should be ignored.')
  command = subcommand_class(root_dir, parser, subcommand_args)

  result = command.execute()
  sys.exit(result)

if __name__ == '__main__':
  main()
