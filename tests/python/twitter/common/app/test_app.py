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

from collections import defaultdict
from functools import partial
import threading
import time
import unittest
import sys

from twitter.common import options
from twitter.common.app import Module
from twitter.common.app.application import Application
from twitter.common.exceptions import ExceptionalThread

import pytest


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
      Module.__init__(self, label=name, dependencies=dependencies)

    class_methods = { '__init__': __init__ }
    if with_setup: class_methods.update(setup_function = setup_function)
    if with_teardown: class_methods.update(teardown_function = teardown_function)
    AnonymousModule = type('AnonymousModule', (Module,), class_methods)
    return AnonymousModule(name, self._values)


class TestApp(unittest.TestCase):
  def setUp(self):
    Module.clear_registry()
    self.factory = ModuleFactory()

  def test_app_registry_basic(self):
    self.factory.new_module('hello')
    app = Application(force_args=['--app_debug'])
    app.init()
    assert self.factory.value('hello') == 1, (
        'initialization functions should be invoked on app.init')

  def test_app_registry_dependencies_simple(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies='second')
    app = Application(force_args=[])
    app.init()
    assert self.factory.value('first') > 0, 'first callback should be called'
    assert self.factory.value('second') > 0, 'second callback should be called'
    assert self.factory.value('third') > 0, 'third callback should be called'
    assert self.factory.value('first') < self.factory.value('second'), (
        'second callback should be called after first')
    assert self.factory.value('second') < self.factory.value('third'), (
        'third callback should be called after second')

  # TODO(wickman) Add cyclic dependency detection and a test.
  def test_app_registry_dependencies_of_list(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies=['first', 'second'])
    app = Application(force_args=[])
    app.init()
    assert self.factory.value('first') > 0, 'first callback should be called'
    assert self.factory.value('second') > 0, 'second callback should be called'
    assert self.factory.value('third') > 0, 'third callback should be called'
    assert self.factory.value('first') < self.factory.value('third'), (
        'third callback should be called after first')
    assert self.factory.value('second') < self.factory.value('third'), (
        'third callback should be called after first')

  def test_app_registry_exit_functions(self):
    self.factory.new_module('first')
    self.factory.new_module('second', dependencies='first')
    self.factory.new_module('third', dependencies=['first', 'second'])
    app = Application(force_args=[])
    app.init()
    app._state = app.SHUTDOWN
    app._run_module_teardown()
    assert self.factory.value('third_exit') > 0 and (
      self.factory.value('second_exit') > 0 and self.factory.value('first_exit') > 0), (
      'all exit callbacks should have been called')
    assert self.factory.value('third_exit') < self.factory.value('second_exit')
    assert self.factory.value('third_exit') < self.factory.value('first_exit')

  def test_app_cyclic_dependencies(self):
    self.factory.new_module('first', dependencies='second')
    with pytest.raises(Module.DependencyCycle):
      self.factory.new_module('second', dependencies='first')

  def test_app_add_options_with_raw(self):
    # raw option
    app = Application(force_args=['--option1', 'option1value', 'extraargs'])
    app.add_option('--option1', dest='option1')
    app.init()
    assert app.get_options().option1 == 'option1value'
    assert app.argv() == ['extraargs']

  def test_app_add_options_with_Option(self):
    # options.Option
    app = Application(force_args=['--option1', 'option1value', 'extraargs'])
    opt = options.Option('--option1', dest='option1')
    app.add_option(opt)
    app.init()
    assert app.get_options().option1 == 'option1value'
    assert app.argv() == ['extraargs']

  def test_app_copy_command_options(self):
    option1 = options.TwitterOption('--test1')
    option2 = options.TwitterOption('--test2')

    app = Application()

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

    app = Application()

    @app.command_option(option)
    def test_command():
      pass

    assert not hasattr(app.get_options(), option_name)
    app.add_command_options(test_command)
    assert hasattr(app.get_options(), option_name)


class TestApplication(Application):
  def __init__(self, main_method, force_args=[]):
    super(TestApplication, self).__init__(exit_function=self.__exit_function, force_args=force_args)
    self.__main_method = main_method
    self.exited = threading.Event()
    self.exited_rc = None

  def _find_main_method(self):
    return self.__main_method

  def __exit_function(self, rc):
    self.exited_rc = rc
    self.exited.set()


def test_application_basic():
  def excepting_method():
    1 / 0

  def exiting_method():
    sys.exit(2)

  def normal_method():
    return 3

  def run_app(method):
    app = TestApplication(method)
    app.main()
    return app

  app = run_app(excepting_method)
  assert app.exited_rc == 1

  app = run_app(exiting_method)
  assert app.exited_rc == 2

  app = run_app(normal_method)
  assert app.exited_rc == 3


def test_application_main_arguments():
  def main_no_args():
    return 0

  app = TestApplication(main_no_args, force_args=[])
  app.main()
  assert app.exited_rc == 0

  app = TestApplication(main_no_args, force_args=['a', 'b', 'c'])
  app.main()
  assert app.exited_rc == 1

  def main_with_args(args):
    assert args == ['a', 'b', 'c']
    return 0

  app = TestApplication(main_with_args, force_args=['a', 'b', 'c'])
  app.main()
  assert app.exited_rc == 0

  def main_with_args_and_options(args, options):
    assert args == ['a', 'b']
    assert options.foo == 'bar'
    return 0

  app = TestApplication(main_with_args_and_options, force_args=['--foo=bar', 'a', 'b'])
  app.add_option('--foo')
  app.main()
  assert app.exited_rc == 0

  def main_with_wrong_args(args, options, herp, derp):
    return 0
  app = TestApplication(main_with_wrong_args)
  app.main()
  assert app.exited_rc == 1

  broken_main = 'not a function'
  app = TestApplication(broken_main)
  app.main()
  assert app.exited_rc == 1


def test_application_commands():
  app = TestApplication(None)
  app.main()
  assert app.exited_rc == 1

  app = TestApplication(None)
  @app.default_command
  def not_main():
    return 0
  app.main()
  assert app.exited_rc == 0

  def real_main():
    return 0
  app = TestApplication(real_main)
  @app.default_command
  def not_main():
    return 0
  app.main()
  assert app.exited_rc == 1

  app = TestApplication(None)
  @app.command
  def command_but_not_default():
    return 0
  app = TestApplication(None)
  app.main()
  assert app.exited_rc == 1


def test_application_selects_command():
  def real_main():
    return 0

  def make_app_with_commands(*args, **kw):
    app = TestApplication(*args, **kw)

    @app.command
    def command_two(args):
      assert args == ['a', 'b']
      return 2

    @app.command
    def command_three():
      return 3

    @app.command
    @app.command_option('--foo', action='store_true', default=False)
    def command_four(args, options):
      return 100 + int(options.foo)

    return app

  app = make_app_with_commands(real_main, force_args=['a', 'b'])
  # real_main doesn't take extra arguments
  app.main()
  assert app.exited_rc == 1

  app = make_app_with_commands(real_main, force_args=['command_two', 'a', 'b'])
  app.main()
  assert app.exited_rc == 2

  app = make_app_with_commands(real_main, force_args=['command_three', 'a', 'b'])
  # command_three doesn't take args
  app.main()
  assert app.exited_rc == 1

  app = make_app_with_commands(real_main, force_args=['command_three'])
  app.main()
  assert app.exited_rc == 3

  app = make_app_with_commands(real_main, force_args=['command_four', '--foo'])
  app.main()
  assert app.exited_rc == 101

  app = make_app_with_commands(real_main, force_args=['command_four'])
  app.main()
  assert app.exited_rc == 100

  app = make_app_with_commands(None, force_args=[])
  # No main and no default command
  app.main()
  assert app.exited_rc == 1

  app = make_app_with_commands(None, force_args=[])
  @app.default_command
  def actual_main():
    return 0
  app.main()
  assert app.exited_rc == 0

  app = TestApplication(None, force_args=[])
  app.main()
  # No main defined at all
  assert app.exited_rc == 1


def test_shutdown_commands():
  shutdown1 = threading.Event()
  shutdown2 = threading.Event()
  shutdown_rc = []
  def shutdown_command(event, rc):
    shutdown_rc.append((rc, event))
    event.set()

  def simple_main():
    return 0

  app = TestApplication(simple_main)
  app.register_shutdown_command(partial(shutdown_command, shutdown1))
  app.register_shutdown_command(partial(shutdown_command, shutdown2))
  app.main()

  shutdown1.wait(timeout=1.0)
  shutdown2.wait(timeout=1.0)
  assert shutdown_rc == [(0, shutdown1), (0, shutdown2)]
  assert shutdown1.is_set()
  assert shutdown2.is_set()


def test_quitquitquit():
  def main():
    app.wait_forever()

  def wait_and_quit():
    time.sleep(0.5)
    app.quitquitquit()

  stop_thread = ExceptionalThread(target=wait_and_quit)
  stop_thread.start()

  app = TestApplication(main)
  app.main()

  assert app.exited_rc == 0


def test_shutdown_exception():
  def shutdown_command(rc):
    1 / 0
  app = TestApplication(lambda: 0)
  app.register_shutdown_command(shutdown_command)
  app.main()
  assert app.exited_rc == 0
