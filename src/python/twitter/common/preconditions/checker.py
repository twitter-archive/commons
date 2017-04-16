# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

import inspect

from types import FunctionType, MethodType

from twitter.common.collections import maybe_list, OrderedDict
from twitter.common.lang import Compatibility

from decorator import decorator


class ArgsMapper(object):
  """Maps arguments a generic function is called with to their parameter names."""

  @classmethod
  def _get_parameter_names(cls, arg_spec):
    param_names = []
    if arg_spec.args:
      param_names.extend(arg_spec.args)
    if arg_spec.varargs:
      param_names.append(arg_spec.varargs)
    if arg_spec.keywords:
      param_names.append(arg_spec.keywords)
    return param_names

  _MAPPED_FUNCS = {}

  @classmethod
  def for_func(cls, func):
    """Return the ``ArgsMapper`` for the given function.

    The ``call_args`` function of the returned ``ArgsMapper`` has similar to functionality to
    currying ``inspect.getcallargs`` with ``func`` in python 2.7+.

    :param func: A function or bound method to return an ``ArgsMapper`` for.
    """
    mapper = cls._MAPPED_FUNCS.get(func)
    if mapper is not None:
      return mapper

    arg_spec = inspect.getargspec(func)

    param_names = cls._get_parameter_names(arg_spec)

    non_kwarg_names = param_names[:-1] if arg_spec.keywords else param_names
    defaults = arg_spec.defaults or ()
    if arg_spec.varargs:
      defaults += ((),)

    def positional_extractor(idx, name):
      def extract(*args, **_):
        if idx < len(args):
          return name, tuple(args[idx:]) if name == arg_spec.varargs else args[idx]
        else:
          return name, defaults[(len(non_kwarg_names) - len(defaults) - idx)]
      return extract

    extractors = []
    for index, non_kwarg_name in enumerate(non_kwarg_names):
      extractors.append(positional_extractor(index, non_kwarg_name))
    if arg_spec.keywords:
      extractors.append(lambda *_, **kwargs: (arg_spec.keywords, kwargs))

    def args_mapper(*args, **kwargs):
      return OrderedDict(map(lambda extractor: extractor(*args, **kwargs), extractors))

    mapper = cls(func, param_names, args_mapper)
    cls._MAPPED_FUNCS[func] = mapper
    return mapper

  def __init__(self, func, param_names, args_mapper):
    self._func = func
    self._param_names = param_names
    self._args_mapper = args_mapper

  @property
  def func(self):
    """Returns the function (or bound method) this ``ArgsMapper`` can map arguments for."""
    return self._func

  @property
  def param_names(self):
    """Returns a list of all the mapped function's parameter names in declaration order."""
    return self._param_names

  def call_args(self, *args, **kwargs):
    """Returns a mapping of arg name to value for each argument passed to the mapped function.

    :param args: The positional arguments the mapped function was called with.
    :param kwargs: The keyword arguments the mapped function was called with.
    """
    return self._args_mapper(*args, **kwargs)


class CallInfo(object):
  """Describes a function call."""

  @classmethod
  def create(cls, mapper, *args, **kwargs):
    return cls(mapper.func, mapper.param_names, mapper.call_args(*args, **kwargs))

  def __init__(self, func, param_names, call_args):
    self._func = func
    self._param_names = param_names
    self._call_args = call_args

  @property
  def func(self):
    """Returns the function (or bound method) that was called."""
    return self._func

  @property
  def param_names(self):
    """Returns the complete list of explicit parameter names for the called function."""
    return self._param_names

  @property
  def call_args(self):
    """Returns a mapping of the actual arguments passed to the function keyed by parameter name."""
    return self._call_args


class CheckError(ValueError):
  """Indicates a precondition check failure."""


class Checker(object):
  """Provides a basis for writing precondition check decorators.

  To implement a precondition check decorator just create a new ``Checker`` and add checks to it.

  Checks can be disabled globally via ``Checker.disable()`` in which case functions decorated
  subsequently will not in fact be decorated.
  """

  _DISABLED = False

  @classmethod
  def disabled(cls):
    """Returns ``True`` if precondition checks are disabled."""
    return cls._DISABLED

  @classmethod
  def disable(cls):
    """Disables preconditions checks if not already disabled.

    NB: This only disables checks for all functions defined after the point this method is called.
    """
    cls._DISABLED = True

  @classmethod
  def enable(cls):
    """Enables preconditions checks if not already enabled.

    NB: This only enables checks for all functions defined after the point this method is called.
    """
    cls._DISABLED = False

  @classmethod
  def one_of(cls, *checkers):
    """Combines multiple checkers into one that ensures at least one checker passes.

    The precondition check decorated function will short circuit at the 1st check that does not fail
    preconditions and execute the wrapped function.

    :param checkers: The checkers to apply in application order
    """
    checkers = maybe_list(checkers, expected_type=Checker)
    if len(checkers) == 0:
      return lambda func: func
    elif len(checkers) == 1:
      return checkers[0]

    class OneOf(cls):
      def add_check(self, check):
        for checker in checkers:
          checker.add_check(check)
        return self

      def __call__(self, func):
        checked_funcs = map(lambda checker: checker(func), checkers)

        def checked(f, *args, **kwargs):
          errors = []
          for checked_func in checked_funcs:
            try:
              return checked_func(*args, **kwargs)
            except CheckError as e:
              errors.append(e)
          raise CheckError('Multiple checks failed:\n  %s' % '\n  '.join(map(str, errors)))

        return decorator(checked, func)

    return OneOf()

  def __init__(self):
    self._checks = []

  @staticmethod
  def _check_check(check):
    if not callable(check):
      raise ValueError('Checks must be callables, given %r of type %r' % (check, type(check)))
    getargspec = inspect.getargspec(check)
    if not len(getargspec.args or ()) == 1:
      raise ValueError('Checks must accept 1 argument, given %r with arg '
                       'info %r' % (check, getargspec))

  def add_check(self, check):
    """Adds a check to this decorator.

    :param check: A callable that accepts a single `CallInfo` argument and signals a check error in
        one of 3 ways:

          * returns ``False``
          * returns a string describing the error
          * raises a :class:`CheckError`
    """
    self._check_check(check)
    self._checks.append(check)
    return self

  def check_decoration(self, mapper):
    """Subclasses can override to validate the decoration at function definition (import) time.

    :param mapper: The :class:`ArgsMapper` for the decorated function.
    """

  def __call__(self, func):
    """Decorates ``func`` with precondition checks if checks are enabled."""
    if self.disabled():
      return func

    if not isinstance(func, (FunctionType, MethodType)):
      raise ValueError('Only functions and methods can be decorated with checks, given %r of '
                       'type %r' % (func, type(func)))

    mapper = ArgsMapper.for_func(func)
    self.check_decoration(mapper)

    def checked(f, *args, **kwargs):
      call_info = CallInfo.create(mapper, *args, **kwargs)
      for check in self._checks:
        result = check(call_info)
        if result is False:
          raise CheckError('Check %r failed for %r with '
                           'args %r' % (check, call_info.func, call_info.call_args))
        if isinstance(result, Compatibility.string):
          raise CheckError(result)

      return func(*args, **kwargs)

    # Preserves signature allowing stacking of checks.
    return decorator(checked, func)


def type_check(allowed_types, name=None, allow_none=True):
  """Returns a check that a value is an instance of one of the allowed types.

  :param allowed_types: One or more types.
  :param string name: An optional name for the checked values.
  :param bool allow_none: ``False`` to disallow ``None`` values to pass this check.
  """
  allowed_types = tuple(maybe_list(allowed_types, expected_type=type))

  def check(value):
    if value is None and allow_none:
      return
    if not isinstance(value, allowed_types):
      return ('%s does not conform to the expected types %r; '
              'got value %r of type %r' % ('`%s`' % name if name else 'Parameter',
                                           ', '.join(map(repr, allowed_types)),
                                           value,
                                           type(value)))
  return check


class ArgChecker(Checker):
  """A precondition check for a single argument."""

  def __init__(self, name, allowed_types=None, required=True):
    """Creates a precondition check for a single argument.

    :param string name: The parameter name to check.
    :param allowed_types: zero or more types the parameter values must be instances of.  If zero
        allowed types are passed (``None`` or an empty iterable will do) then all parameter values
        will be accepted with the possible exception of ``None`` if ``required`` is ``True``.
    :param bool required: ``False`` if the parameter need not be passed; ie: the parameter is
        allowed to take a value of ``None``)
    """
    super(ArgChecker, self).__init__()

    if not isinstance(name, Compatibility.string):
      raise ValueError('The `name` parameter must be the string name of the parameter to check, '
                       'given %r of type %r' % (name, type(name)))

    self._allowed_types = tuple(maybe_list(allowed_types or (), expected_type=type))

    if not isinstance(required, bool):
      raise ValueError('The `required` parameter must be a bool, given %r of '
                       'type %r' % (required, type(required)))

    self._name = name

    if required:
      def check_not_none(value):
        if value is None:
          return 'Parameter `%s` is required' % name
      self.add_check(check_not_none)

    if allowed_types:
      self.add_check(type_check(allowed_types, name=name))

  def add_check(self, check):
    """Adds a value check to this decorator.

    NB: The given checks must take a single value argument that is the actual value of the checked
    argument.  This is unlike the checks added to the :class:`Checker` base-class.

    :param check: A callable that accepts a single value argument and signals a check error in
        one of 3 ways:

          * return ``False``
          * return a string describing the error
          * raise a :class:`CheckError`
    """
    self._check_check(check)

    def arg_check(call_info):
      return check(call_info.call_args[self._name])
    return super(ArgChecker, self).add_check(arg_check)

  def check_decoration(self, mapper):
    if self._name not in mapper.param_names:
      raise KeyError('The parameter name `%s` is not a parameter name of the decorated function %r '
                     'with parameters: %s' % (self._name,
                                              mapper.func,
                                              ', '.join(map(repr, mapper.param_names))))
