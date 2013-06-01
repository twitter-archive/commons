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
    pants(':test_pep8'),
    pants(':test_pyflakes'),
    pants(':test_twitter_plugins'),
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

python_tests(
  name = 'test_twitter_plugins',
  sources = ['test_import_order.py',
             'test_variable_names.py',
             'test_print_statements.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle/plugins:twitter'),
  ]
)
