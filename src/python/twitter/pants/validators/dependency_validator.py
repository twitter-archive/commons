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

from twitter.pants.targets import JvmBinary, PythonBinary, JvmApp
from twitter.pants.validators import ValidationError


def check_invalid_binary_deps(context):
  """Validates binary dependencies present in the execution Context.

  Following rule statements are asserted to validate the context.

     Only a JvmApp can depend upon a JvmBinary.
     Nothing should depend upon a PythonBinary.

  :param context: pants.goal.context.Context object
  :raises ValidationError: on failure
  """

  def is_not_jvmapp(tgt):
    return not isinstance(tgt, JvmApp)

  def is_jvm_binary(tgt):
    return isinstance(tgt, JvmBinary)

  def is_python_binary(tgt):
    return isinstance(tgt, PythonBinary)

  invalid_deps_rulemap = {
    is_not_jvmapp: is_jvm_binary,
    None: is_python_binary
  }

  invalid_deps = []

  for from_rule, on_rule in invalid_deps_rulemap.iteritems():
    invalid_dependency = context.dependants(from_predicate=from_rule, on_predicate=on_rule)
    for from_dep, to_invalid_dep in invalid_dependency.items():
      invalid_deps.append("%s depends on:\n\t\t%s" % (from_dep,
                                                      "\n\t\t".join(sorted(map(str, to_invalid_dep)))))

  if invalid_deps:
    msg = ("Found the following invalid binary dependencies:\n\t%s" % "\n\t".join(invalid_deps))
    raise ValidationError(msg)
