import sys
import inspect

class Inspection(object):
  @staticmethod
  def _find_main_from_caller():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if 'main' in stack[fr_n][0].f_locals:
        return stack[fr_n][0].f_locals['main']
    return None

  @staticmethod
  def _print_stack_locals(out=sys.stderr):
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      print >> out, '--- frame %s ---\n' % fr_n
      for key in stack[fr_n][0].f_locals:
        print >> out, '  %s => %s' % (
          key, stack[fr_n][0].f_locals[key])

  @staticmethod
  def _find_main_module():
    stack = inspect.stack()[1:]
    for fr_n in range(len(stack)):
      if 'main' in stack[fr_n][0].f_locals:
        return stack[fr_n][0].f_locals['__name__']
    return None
