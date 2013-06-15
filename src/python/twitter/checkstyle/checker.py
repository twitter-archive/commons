from __future__ import print_function

from twitter.common import app

from .common import Nit, PythonFile
from .iterators import git_iterator, path_iterator
from .plugins import list_plugins


app.add_option(
  '-p',
  action='append',
  type='str',
  default=[],
  dest='plugins',
  help='Explicitly list plugins to enable.')


app.add_option(
  '--diff',
  type='str',
  default=None,
  dest='diff',
  help='If specified, only checkstyle against the diff of the supplied branch, e.g. --diff=master')


app.add_option(
  '-s', '--severity',
  default='COMMENT',
  type='choice',
  choices=('COMMENT', 'WARNING', 'ERROR'),
  dest='severity',
  help='Only messages at this severity or higher are logged.  Options: COMMENT, WARNING, ERROR.')


app.add_option(
  '--strict',
  default=False,
  action='store_true',
  dest='strict',
  help='If enabled, have non-zero exit status for any nit at WARNING or higher.')


def main(args, options):
  plugins = list_plugins()
  plugin_map = dict((plugin.__name__, plugin) for plugin in plugins)

  if options.plugins:
    plugins = filter(None, (plugin_map.get(plugin_name) for plugin_name in options.plugins))
    for plugin in plugins:
      print('Selected %s' % plugin.__name__)

  if options.diff:
    iterator = git_iterator(args, options)
  else:
    iterator = path_iterator(args, options)

  severity = Nit.COMMENT
  for number, name in Nit.SEVERITY.items():
    if name == options.severity:
      severity = number

  should_fail = False
  for filename, line_filter in iterator:
    try:
      python_file = PythonFile.parse(filename)
    except SyntaxError as e:
      print('%s:SyntaxError: %s' % (filename, e))
      continue
    for checker in plugins:
      for nit in checker(python_file, line_filter):
        if nit.severity >= severity:
          print(nit)
          print()
        should_fail |= nit.severity >= Nit.ERROR or (nit.severity >= Nit.WARNING and options.strict)

  return int(should_fail)


app.main()
