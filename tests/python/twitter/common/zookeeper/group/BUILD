python_test_suite(
  name = 'group',
  dependencies = [
    ':test_kazoo_group',
  ],
)

python_library(
  name = 'test_base',
  sources = ['test_base.py'],
  dependencies = [
    'src/python/twitter/common/zookeeper:testing',
  ]
)

python_library(
  name = 'test_kazoo_group_lib',
  sources = ['test_kazoo_group.py'],
  dependencies = [
    ':test_base',
    'src/python/twitter/common/zookeeper:testing',
  ]
)

python_tests(
  name = 'test_kazoo_group',
  sources = ['test_active_kazoo_group.py', 'test_kazoo_group.py'],
  dependencies = [
    '3rdparty/python:kazoo',
    '3rdparty/python:mock',
    ':test_kazoo_group_lib',
    'src/python/twitter/common/zookeeper/group:kazoo_group',
  ],
  coverage = 'twitter.common.zookeeper.group.kazoo_group'
)
