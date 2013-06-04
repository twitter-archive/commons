python_test_suite(
  name = 'all',
  dependencies = [
    pants(':client'),
    pants(':kazoo_client'),
    pants('tests/python/twitter/common/zookeeper/group'),
    pants('tests/python/twitter/common/zookeeper/serverset:all'),
  ],
)

python_tests(
  name = 'client',
  timeout = Amount(10, Time.MINUTES),
  dependencies = [
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/zookeeper'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  sources = ['client_test.py'],
  coverage = 'twitter.common.zookeeper.client'
)

python_tests(
  name = 'kazoo_client',
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/zookeeper:kazoo_client'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  sources = ['kazoo_client_test.py'],
  coverage = 'twitter.common.zookeeper.kazoo_client'
)
