python_test_suite(
  name = 'all',
  dependencies = [
    ':client',
  ],
)

python_tests(
  name = 'client',
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    '3rdparty/python:thrift-0.7',
    'src/python/twitter/common/zookeeper/serversets',
    'src/python/twitter/common/zookeeper:testing',
  ],
  sources = globs('client_test.py'),
)
