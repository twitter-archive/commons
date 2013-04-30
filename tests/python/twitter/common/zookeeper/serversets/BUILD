python_test_suite(
  name = 'all',
  dependencies = [
    pants(':client'),
  ],
)

python_tests(
  name = 'client',
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    pants('3rdparty/python:thrift'),
    pants('src/python/twitter/common/zookeeper/serversets'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  sources = globs('client_test.py'),
)
