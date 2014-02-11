python_test_suite(
  name = 'all',
  dependencies = [
    pants(':endpoint'),
    pants(':test_kazoo_serverset'),
    pants(':test_serverset_unit'),
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
  name = 'test_kazoo_serverset',
  sources = ['test_kazoo_serverset.py'],
  timeout = Amount(5, Time.MINUTES),
  dependencies = [
    pants(':test_base'),
    pants('src/python/twitter/common/zookeeper/serverset:kazoo_serverset'),
  ],
)

python_tests(
  name = 'endpoint',
  sources = ['test_endpoint.py'],
  dependencies = [
    pants('src/python/twitter/common/zookeeper/serverset:serverset_base')
  ]
)

python_tests(
  name = 'test_serverset_unit',
  sources = ['test_serverset_unit.py'],
  dependencies = [
    pants('3rdparty/python:mock'),
    pants('3rdparty/python:kazoo'),
    pants('src/python/twitter/common/zookeeper/group:group_base'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
    pants('src/python/twitter/common/zookeeper/serverset'),
  ],
)
