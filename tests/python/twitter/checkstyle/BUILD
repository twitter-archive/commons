python_test_suite(
  name = 'all',
  dependencies = [
    pants(':checkstyle'),
  ]
)

python_test_suite(
  name = 'checkstyle',
  dependencies = [
    pants(':test_common'),
    pants(':test_git_iterators'),
    pants(':test_iterators'),
    pants(':test_noqa'),
    pants('tests/python/twitter/checkstyle/plugins:all'),
  ]
)

python_tests(
  name = 'test_common',
  sources = ['test_common.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:common'),
  ]
)

python_tests(
  name = 'test_git_iterators',
  sources = ['test_git_iterators.py'],
  dependencies = [
    pants('3rdparty/python:git-python'),
    pants('src/python/twitter/checkstyle:iterators'),
  ]
)

python_tests(
  name = 'test_iterators',
  sources = ['test_iterators.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:iterators'),
  ]
)

python_tests(
  name = 'test_noqa',
  sources = ['test_noqa.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:checker'),
    pants('src/python/twitter/checkstyle:common'),
  ]
)
