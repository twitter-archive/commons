python_test_suite(
  name = 'all',
  dependencies = [
    ':endpoint',
    ':test_kazoo_serverset',
    ':test_serverset_unit',
  ],
)

python_library(
  name = 'test_base',
  sources = ['test_base.py'],
  dependencies = [
    'src/python/twitter/common/zookeeper:testing',
  ]
)

python_tests(
  name = 'test_kazoo_serverset',
  sources = ['test_kazoo_serverset.py'],
  dependencies = [
    ':test_base',
    'src/python/twitter/common/zookeeper/serverset:kazoo_serverset',
  ],
)

python_tests(
  name = 'endpoint',
  sources = ['test_endpoint.py'],
  dependencies = [
    'src/python/twitter/common/zookeeper/serverset:serverset_base'
  ]
)

python_tests(
  name = 'test_serverset_unit',
  sources = ['test_serverset_unit.py'],
  dependencies = [
    '3rdparty/python:mock',
    '3rdparty/python:kazoo',
    'src/python/twitter/common/zookeeper/group:group_base',
    'src/python/twitter/common/zookeeper/group:kazoo_group',
    'src/python/twitter/common/zookeeper/serverset',
  ],
)
