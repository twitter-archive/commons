python_test_suite(
  name = 'group',
  dependencies = [
    ':test_group',
  ],
)

python_tests(
  name = 'test_group',
  sources = ['test_group.py'],
  timeout = Amount(20, Time.MINUTES),
  dependencies = [
    'src/python/twitter/common/zookeeper/group',
    'src/python/twitter/common/zookeeper:testing',
  ]
)
