# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

__author__ = 'Benjy Weinberger'


from twitter.pants.targets import JavaLibrary, JavaTests, ScalaLibrary, ScalaTests
from twitter.pants.tasks import Task
from twitter.pants.tasks.binary_utils import profile_classpath, runjava


def is_jvm(target):
  return isinstance(target, JavaLibrary) or isinstance(target, JavaTests) or \
         isinstance(target, ScalaLibrary) or isinstance(target, ScalaTests)


class ScalaRepl(Task):
  @classmethod
  def setup_parser(cls, option_group, args, mkflag):
    option_group.add_option(mkflag("jvmargs"), dest = "run_jvmargs", action="append",
      help = "Run the repl in a jvm with these extra jvm args.")

  def __init__(self, context):
    Task.__init__(self, context)
    self.jvm_args = context.config.getlist('scala-repl', 'jvm_args', default=[])
    if context.options.run_jvmargs:
      self.jvm_args.extend(context.options.run_jvmargs)
    self.confs = context.config.getlist('scala-repl', 'confs')
    self.profile = context.config.get('scala-repl', 'profile')
    self.main = context.config.get('scala-repl', 'main')

  def execute(self, targets):
    classpath = profile_classpath(self.profile)
    with self.context.state('classpath', []) as cp:
      classpath.extend(jar for conf, jar in cp if conf in self.confs)

    result = runjava(
      jvmargs=self.jvm_args,
      classpath=classpath,
      main=self.main,
      args=[]
    )

