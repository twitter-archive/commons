import sys

if sys.version_info[0] == 3:
  def exec_function(ast, globals_map):
    locals_map = globals_map
    exec(ast, globals_map, locals_map)
    return locals_map
else:
  eval(compile("""\
def exec_function(ast, globals_map):
  locals_map = globals_map
  exec ast in globals_map, locals_map
  return locals_map
""", "<exec_function>", "exec"))
