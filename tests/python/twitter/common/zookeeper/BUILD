python_test_suite(
  name = 'all',
  dependencies = [
    pants(':client'),
    pants('tests/python/twitter/common/zookeeper/serversets:all'),
  ],
)

python_tests(
  name = 'client',
  dependencies = [
    pants('3rdparty/python:thrift-0.7'),
    pants('src/python/twitter/common/zookeeper'),
  ],
  sources = globs('client_test.py'),
)
