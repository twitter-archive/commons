python_test_suite(
  name = 'all',
  dependencies = [
    ':test_serverset',
  ],
)

python_tests(
  name = 'test_serverset',
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    'src/python/twitter/common/zookeeper/serverset',
    'src/python/twitter/common/zookeeper:testing',
  ],
  sources = globs('test_serverset.py'),
)
