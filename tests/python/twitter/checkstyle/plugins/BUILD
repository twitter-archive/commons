python_test_suite(
  name = 'all',
  dependencies = [
    pants(':plugins'),
  ]
)

python_tests(
  name = 'plugins',
  sources = globs('*.py'),
  dependencies = [
    pants('src/python/twitter/checkstyle/plugins:twitter'),
  ]
)
