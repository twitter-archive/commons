python_test_suite(
  name = 'all',
  dependencies = [
    pants(':client'),
    pants('tests/python/twitter/common/zookeeper/group'),
    pants('tests/python/twitter/common/zookeeper/serverset:all'),
    pants('tests/python/twitter/common/zookeeper/serversets:all'),
  ],
)

python_tests(
  name = 'client',
  timeout = Amount(10, Time.MINUTES),
  dependencies = [
    pants('3rdparty/python:thrift-0.7'),
    pants('src/python/twitter/common/zookeeper'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  sources = globs('client_test.py'),
)
