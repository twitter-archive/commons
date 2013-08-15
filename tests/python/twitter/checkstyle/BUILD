python_test_suite(
  name = 'all',
  dependencies = [
    pants(':checkstyle')
  ]
)

python_test_suite(
  name = 'checkstyle',
  dependencies = [
    pants(':test_common'),
    pants(':test_git_iterators'),
    pants(':test_iterators'),
    pants(':test_pep8'),
    pants(':test_pyflakes'),
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
  name = 'test_pep8',
  sources = ['test_pep8.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle/plugins:pep8'),
  ]
)

python_tests(
  name = 'test_pyflakes',
  sources = ['test_pyflakes.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle/plugins:pyflakes'),
  ]
)
