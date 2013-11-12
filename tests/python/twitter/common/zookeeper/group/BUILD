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

python_library(
  name = 'test_kazoo_group_lib',
  sources = ['test_kazoo_group.py'],
  dependencies = [
    pants(':test_base'),
    pants('src/python/twitter/common/zookeeper:testing'),
  ]
)

python_tests(
  name = 'test_kazoo_group',
  sources = ['test_active_kazoo_group.py', 'test_kazoo_group.py'],
  timeout = Amount(20, Time.MINUTES),
  dependencies = [
    pants('3rdparty/python:kazoo'),
    pants('3rdparty/python:mock'),
    pants(':test_kazoo_group_lib'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
  ],
  coverage = 'twitter.common.zookeeper.group.kazoo_group'
)
