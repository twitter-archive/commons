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

from twitter.common.lang import Singleton
from twitter.pants.goal.context import Context


class ValidationError(Exception):
  """Raised by validators to when a context fails validation."""

  def __init__(self, msg=None):
    if not msg:
      msg = "Pants Validation Error"
    self.msg = msg


class ContextValidator(Singleton):
  """Validate pants context object.

  This is a Singleton class, all validator functions are installed to a single instance.
  A validator function is required to take context object as it's only argument and raise
  ValidationError if the pants context fails validation.
  """

  def __init__(self):
    self.validators = set()

  @property
  def _validators(self):
    return frozenset(self.validators)

  def install(self, *validators):
    """ Install validator functions to the ContextValidator instance.
    A validator function is supposed to take a single argument. 

    :raises: ValueError if validator function does not follow the spec.
    """
    for validator in validators:
      args, varargs, keywords, defaults = inspect.getargspec(validator)
      if varargs or keywords or defaults:
        raise ValueError("ContextValidator %s cannot accept varargs, "
                         "keywords or defaults" % str(validator))
      if len(args) > 1:
        raise ValueError("ContextValidator %s can accept only one arg." % str(validator))
      self.validators.add(validator)

  def validate(self, ctx):
    """ Validates the context.

    :raises ValueError: if an argument other an pants context object is passed.
    :raises ValidationError: if pants context fails validation.
    """
    validation_errors=[]
    if not isinstance(ctx, Context):
      raise ValueError("Context object is required. Found %s" % str(ctx))
    for validator in self._validators:
      try:
        validator(ctx)
      except ValidationError as verr:
        validation_errors.append(verr.msg)
    if validation_errors:
      raise ValidationError("\n".join(validation_errors))

  def clear(self):
    self.validators.clear()
