python_test_suite(
  name = 'all',
  dependencies = [
    ':client',
    'tests/python/twitter/common/zookeeper/group',
    'tests/python/twitter/common/zookeeper/serverset:all',
    'tests/python/twitter/common/zookeeper/serversets:all',
  ],
)

python_tests(
  name = 'client',
  timeout = Amount(10, Time.MINUTES),
  dependencies = [
    '3rdparty/python:thrift-0.7',
    'src/python/twitter/common/zookeeper',
    'src/python/twitter/common/zookeeper:testing',
  ],
  sources = globs('client_test.py'),
)
