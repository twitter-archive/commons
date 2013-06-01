from twitter.checkstyle.plugins.variable_names import (
  allow_underscores,
  is_builtin_name,
  is_lower_snake,
  is_reserved_name,
  is_reserved_with_trailing_underscore,
  is_upper_camel,
)


def test_allow_underscores():
  @allow_underscores(0)
  def no_underscores(name):
    return name
  assert no_underscores('foo') == 'foo'
  assert no_underscores('foo_') == 'foo_'
  assert no_underscores('_foo') is False
  assert no_underscores('__foo') is False

  @allow_underscores(1)
  def one_underscore(name):
    return name
  assert one_underscore('foo') == 'foo'
  assert one_underscore('_foo') == 'foo'
  assert one_underscore('_foo_') == 'foo_'
  assert one_underscore('__foo') is False
  assert one_underscore('___foo') is False


UPPER_CAMEL = (
  'Rate',
  'HTTPRate',
  'HttpRate',
  'Justastringofwords'
)

LOWER_SNAKE = (
  'quiet',
  'quiet_noises',
)


def test_is_upper_camel():
  for word in UPPER_CAMEL:
    assert is_upper_camel(word)
    assert is_upper_camel('_' + word)
    assert not is_upper_camel('__' + word)
    assert not is_upper_camel(word + '_')
  for word in LOWER_SNAKE:
    assert not is_upper_camel(word)
    assert not is_upper_camel('_' + word)
    assert not is_upper_camel(word + '_')


def test_is_lower_snake():
  for word in LOWER_SNAKE:
    assert is_lower_snake(word)
    assert is_lower_snake('_' + word)
    assert is_lower_snake('__' + word)
  for word in UPPER_CAMEL:
    assert not is_lower_snake(word)
    assert not is_lower_snake('_' + word)


def test_is_builtin_name():
  assert is_builtin_name('__foo__')
  assert not is_builtin_name('__fo_o__')
  assert not is_builtin_name('__Foo__')
  assert not is_builtin_name('__fOo__')
  assert not is_builtin_name('__foo')
  assert not is_builtin_name('foo__')


def test_is_reserved_name():
  for name in ('for', 'super', 'id', 'type', 'class'):
    assert is_reserved_name(name)
  assert not is_reserved_name('none')


def test_is_reserved_with_trailing_underscore():
  for name in ('super', 'id', 'type', 'class'):
    assert is_reserved_with_trailing_underscore(name + '_')
    assert not is_reserved_with_trailing_underscore(name + '__')
  for name in ('garbage', 'slots', 'metaclass'):
    assert not is_reserved_with_trailing_underscore(name + '_')


PYTHON_STATEMENT = """
"""
