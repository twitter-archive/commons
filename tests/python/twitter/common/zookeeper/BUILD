python_test_suite(
  name = 'all',
  dependencies = [
    ':kazoo_client',
    'tests/python/twitter/common/zookeeper/group',
    'tests/python/twitter/common/zookeeper/serverset:all',
  ],
)

python_tests(
  name = 'kazoo_client',
  dependencies = [
    'src/python/twitter/common/log',
    'src/python/twitter/common/zookeeper:kazoo_client',
    'src/python/twitter/common/zookeeper:testing',
  ],
  sources = ['kazoo_client_test.py'],
  coverage = 'twitter.common.zookeeper.kazoo_client'
)
