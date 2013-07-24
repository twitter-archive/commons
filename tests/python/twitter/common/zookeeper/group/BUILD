python_test_suite(
  name = 'group',
  dependencies = [
    pants(':test_kazoo_group'),
  ],
)

python_library(
  name = 'test_base',
  sources = ['test_base.py'],
  dependencies = [
    pants('src/python/twitter/common/zookeeper:testing'),
  ]
)

python_tests(
  name = 'test_kazoo_group',
  sources = ['test_kazoo_group.py'],
  timeout = Amount(20, Time.MINUTES),
  dependencies = [
    pants(':test_base'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
  ],
  coverage = 'twitter.common.zookeeper.group.kazoo_group'
)
