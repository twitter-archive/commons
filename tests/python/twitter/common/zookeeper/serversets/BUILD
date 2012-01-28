python_test_suite(
  name = 'all',
  dependencies = [
    pants(':client'),
  ],
)

python_tests(
  name = 'client',
  dependencies = [
    pants('3rdparty/python:thrift-0.7'),
    pants('src/python/twitter/common/zookeeper'),
    pants('src/python/twitter/common/zookeeper/serversets'),
  ],
  sources = globs('client_test.py'),
)
