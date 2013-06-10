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

import sys
from twitter.common import app
from twitter.common import options
from twitter.common.app import Application

import pytest
import unittest
from collections import defaultdict

class ModuleFactory(object):
  def __init__(self):
    self._values = defaultdict(int)

  def value(self, key):
    return self._values[key]

  def new_module(self, name, dependencies=None, with_setup=True, with_teardown=True):
    def setup_function(self):
      self._values['counter'] = self._values['counter'] + 1
      self._values[self.label()] = self._values['counter']
    def teardown_function(self):
      self._values['exit_counter'] = self._values['exit_counter'] + 1
      self._values[self.label() + '_exit'] = self._values['exit_counter']
    def __init__(self, name, values):
      self._values = values
      app.Module.__init__(self, label=name, dependencies=dependencies)

    class_methods = { '__init__': __init__ }
    if with_setup: class_methods.update(setup_function = setup_function)
    if with_teardown: class_methods.update(teardown_function = teardown_function)
    AnonymousModule = type('AnonymousModule', (app.Module,), class_methods)
    return AnonymousModule(name, self._values)


class TestApp(unittest.TestCase):
  def setUp(self):
    app.reset()
    app.Module.clear_registry()
    self.factory = ModuleFactory()

  @pytest.mark.xfail
  def test_app_name(self):
    # This is going to be pytest as long as we invoke all these with
    # sys.interpreter -m pytest <source> since the __entry_point__ will
    # be detected as something like:
    # $HOME/workspace/science/3rdparty/python/pytest-2.0.2-py2.6.egg/pytest.pyc
    # or $HOME/.python-eggs/pants.pex/pytest-.../pytest.pyc
    assert app.name() == 'pytest'
    ALTERNATE_NAME = 'not_test_app_but_something_else'
    app.set_name(ALTERNATE_NAME)
    assert app.name() == ALTERNATE_NAME
    app.init(force_args=[])
    with pytest.raises(app.ApplicationError):
      app.set_name('anything')

  def test_app_registry_basic(self):
    self.factory.new_module('hello')
    app.init(force_args=['--app_debug'])
    assert self.factory.value('hello') == 1, 'initialization functions should be invoked on app.init'

  def test_app_registry_dependencies_simple(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies='second')
    app.init(force_args=[])
    assert self.factory.value('first') > 0, 'first callback should be called'
    assert self.factory.value('second') > 0, 'second callback should be called'
    assert self.factory.value('third') > 0, 'third callback should be called'
    assert self.factory.value('first') < self.factory.value('second'), 'second callback should be called after first'
    assert self.factory.value('second') < self.factory.value('third'), 'third callback should be called after second'

  # TODO(wickman) Add cyclic dependency detection and a test.
  def test_app_registry_dependencies_of_list(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies=['first', 'second'])
    app.init(force_args=[])
    assert self.factory.value('first') > 0, 'first callback should be called'
    assert self.factory.value('second') > 0, 'second callback should be called'
    assert self.factory.value('third') > 0, 'third callback should be called'
    assert self.factory.value('first') < self.factory.value('third'), 'third callback should be called after first'
    assert self.factory.value('second') < self.factory.value('third'), 'third callback should be called after first'

  def test_app_registry_exit_functions(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies=['first', 'second'])
    app.init(force_args=[])
    def exit_function(*args):
      pass
    app.quit(None, exit_function=exit_function)
    assert self.factory.value('third_exit') > 0 and (
      self.factory.value('second_exit') > 0 and self.factory.value('first_exit') > 0), \
      'all exit callbacks should have been called'
    assert self.factory.value('third_exit') < self.factory.value('second_exit')
    assert self.factory.value('third_exit') < self.factory.value('first_exit')

  def test_app_cyclic_dependencies(self):
    self.factory.new_module('first', dependencies='second')
    with pytest.raises(app.Module.DependencyCycle):
      self.factory.new_module('second', dependencies='first')

  def test_app_add_options_with_raw(self):
    # raw option
    app.add_option('--option1', dest='option1')
    app.init(force_args=['--option1', 'option1value', 'extraargs'])
    assert app.get_options().option1 == 'option1value'
    assert app.argv() == ['extraargs']

  def test_app_add_options_with_Option(self):
    # options.Option
    opt = options.Option('--option1', dest='option1')
    app.add_option(opt)
    app.init(force_args=['--option1', 'option1value', 'extraargs'])
    assert app.get_options().option1 == 'option1value'
    assert app.argv() == ['extraargs']

  def test_app_copy_command_options(self):
    option1 = options.TwitterOption('--test1')
    option2 = options.TwitterOption('--test2')

    @app.command_option(option1)
    def test_command():
      pass

    @app.copy_command_options(test_command)
    @app.command_option(option2)
    def test_command_2():
      pass

    assert set([option1, option2]) == set(getattr(test_command_2, Application.OPTIONS_ATTR))

  def test_app_add_command_options(self):
    option_name = 'test_option_name'
    option = options.TwitterOption('--test', dest=option_name)

    @app.command_option(option)
    def test_command():
      pass

    assert not hasattr(app.get_options(), option_name)
    app.add_command_options(test_command)
    assert hasattr(app.get_options(), option_name)
