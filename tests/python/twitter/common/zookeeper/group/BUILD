python_test_suite(
  name = 'group',
  dependencies = [
    pants(':test_group'),
  ],
)

python_tests(
  name = 'test_group',
  sources = ['test_group.py'],
  timeout = Amount(20, Time.MINUTES),
  dependencies = [
    pants('src/python/twitter/common/zookeeper/group'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ],
  coverage = 'twitter.common.zookeeper.group'
)
