python_test_suite(
  name = 'all',
  dependencies = [
    ':plugins',
  ]
)

python_tests(
  name = 'plugins',
  sources = globs('*.py'),
  dependencies = [
    'src/python/twitter/checkstyle/plugins:twitter',
  ]
)
