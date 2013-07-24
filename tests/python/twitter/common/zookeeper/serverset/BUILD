python_test_suite(
  name = 'all',
  dependencies = [
    pants(':test_kazoo_serverset'),
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
