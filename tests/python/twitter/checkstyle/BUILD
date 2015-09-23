python_test_suite(
  name = 'all',
  dependencies = [
    ':checkstyle',
  ]
)

python_test_suite(
  name = 'checkstyle',
  dependencies = [
    ':test_common',
    ':test_git_iterators',
    ':test_iterators',
    ':test_noqa',
    'tests/python/twitter/checkstyle/plugins:all',
  ]
)

python_tests(
  name = 'test_common',
  sources = ['test_common.py'],
  dependencies = [
    'src/python/twitter/checkstyle:common',
  ]
)

python_tests(
  name = 'test_git_iterators',
  sources = ['test_git_iterators.py'],
  dependencies = [
    '3rdparty/python:git-python',
    'src/python/twitter/checkstyle:iterators',
  ]
)

python_tests(
  name = 'test_iterators',
  sources = ['test_iterators.py'],
  dependencies = [
    'src/python/twitter/checkstyle:iterators',
  ]
)

python_tests(
  name = 'test_noqa',
  sources = ['test_noqa.py'],
  dependencies = [
    'src/python/twitter/checkstyle:checker',
    'src/python/twitter/checkstyle:common',
  ]
)
