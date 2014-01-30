from twitter.pants.base import Config
from twitter.pants.base_build_root_test import BaseBuildRootTest
from twitter.pants.goal import Context
from twitter.pants.targets.jvm_binary import JvmBinary
from twitter.pants.validators import validator, ContextValidator, ValidationError


INVALID_JVM_BINARY_JVM_BINARY = """
jvm_binary(name="target",
           basename="target-base",
           main='com.twitter.common.examples.Target',
           dependencies=[pants(':jvmbinary')]
)
jvm_binary(name="jvmbinary",
           basename="jvmbinary-base",
           main="com.twitter.common.examples.JvmBinary",
)
"""

INVALID_JAVA_LIBRARY_JVM_BINARY="""
jvm_binary(name="target",
           basename="target-base",
           main="com.twitter.common.examples.Target",
           dependencies=[pants(':javalibrary')]
)
java_library(name="javalibrary",
             sources=[],
             dependencies=[pants(":jvmbinary")]
)
jvm_binary(name="jvmbinary",
           basename="jvmbinary-base",
)
"""

INVALID_SCALA_LIBRARY_JVM_BINARY="""
jvm_binary(name="target",
           basename="target-base",
           main="com.twitter.common.examples.Target",
           dependencies=[pants(":scalalibrary")]
)
scala_library(name='scalalibrary',
             sources=[],
             dependencies=[pants(":jvmbinary")]
)
jvm_binary(name="jvmbinary",
           basename="jvmbinary-base"
)
"""

INVALID_JVM_BINARY_JVM_APP="""
jvm_binary(name="target",
           basename="target-base",
           main="com.twitter.common.examples.Target",
           dependencies=[pants(':jvmapp')]
)
jvm_app(name="jvmapp",
        basename="jvmapp-base",
        main="com.twitter.common.examples.JvmAppBase",
)
"""


INVALID_SCALA_LIBRARY_PYTHON_BINARY="""
scala_library(name="scalalibrary",
             sources=[],
             dependencies=[pants(":target")]
)
python_binary(name="target")
"""


VALID_BUILD = """
jvm_binary(name='hello',
           basename='jvmhello',
           main='com.twitter.common.examples.Hello',
           dependencies=[pants(':world')]
)
scala_library(name='world',
              sources=["world.scala"]
)
"""


def binary_dep_validator(context):
  for tgt in context._targets:
    if hasattr(tgt, 'dependencies'):
      for dep in tgt.dependencies:
        if isinstance(tgt, JvmBinary) and isinstance(dep, JvmBinary):
          raise ValidationError("Invalid Dependency.")


def bad_validator(context, arg):
    pass

def bad_validator_kws(context=lambda :None):
    pass

class ContextTest(BaseBuildRootTest):

  @classmethod
  def setUpClass(cls):
    super(ContextTest, cls).setUpClass()
    cls.config = Config.load()
    validator.install(binary_dep_validator)

  def create_context(self, **kwargs):
    return Context(ContextTest.config, **kwargs)


  def test_bad_validator(self):
    self.assertRaises(ValueError, validator.install, bad_validator)
    self.assertRaises(ValueError, validator.install, bad_validator_kws)


  def test_validate_invalid_context(self):
    INVALID_BUILD_FILES = [INVALID_JVM_BINARY_JVM_BINARY,
                           INVALID_SCALA_LIBRARY_PYTHON_BINARY,
                           INVALID_JAVA_LIBRARY_JVM_BINARY,
                           INVALID_JVM_BINARY_JVM_APP]
    for BUILD_FILE in INVALID_BUILD_FILES:
      self.create_target('projectA', BUILD_FILE)
      context = self.create_context(options={},
                                    target_roots=[self.target('projectA:target')])
      self.assertRaises(ValidationError, validator.validate, context)

  def test_validate_valid_context(self):
    self.create_target('projectB', VALID_BUILD)
    context = self.create_context(options={},
                                  target_roots=[self.target('projectB:hello')])
    try:
      validator.validate(context)
    except ValidationError:
      raise AssertionError("ValidationError raised for a valid context.")

  def test_singleton_context_validator(self):
      newvalidator = ContextValidator()
      assert(binary_dep_validator in newvalidator._validators)
