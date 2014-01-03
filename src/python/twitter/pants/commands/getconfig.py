from __future__ import print_function

__author__ = "Ted Dziuba"

from . import Command

from twitter.pants.base import Config

class GetConfig(Command):
    """Prints the value of the specified configuration variable to standard output."""

    __command__ = "getconfig"

    def setup_parser(self, parser, args):
        parser.set_usage("\n"
                         " %prog getconfig [varname]\n"
                         " %prog getconfig [section] [varname]")

        parser.epilog = ("Prints the value of a configuration variable "
                         "to standard output.")

    def __init__(self, run_tracker, root_dir, parser, argv):
        Command.__init__(self, run_tracker, root_dir, parser, argv)
        self.varname = None
        self.section = Config.DEFAULT_SECTION

        if not self.args:
            self.error("A varname argument is required.")

        elif len(self.args) == 1:
            self.varname = self.args[0]

        elif len(self.args) == 2:
            self.section = self.args[0]
            self.varname = self.args[1]

        elif len(self.args) > 2:
            self.error("Only [section] and [varname] may be specified")


    def execute(self):
        config = Config.load()
        value = config.get(self.section, self.varname)
        if value is None:
            self.error("No value is defined for [section='%s'] [varname='%s']"
                       % (self.section, self.varname), show_help=False)
        else:
            print(value)
