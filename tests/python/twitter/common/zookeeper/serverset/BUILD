python_test_suite(
  name = 'all',
  dependencies = [
    pants(':test_serverset'),
  ],
)

python_tests(
  name = 'test_serverset',
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    pants('src/python/twitter/common/zookeeper/serverset'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  sources = globs('test_serverset.py'),
  coverage = 'twitter.common.zookeeper.serverset'
)
