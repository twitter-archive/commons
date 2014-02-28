python_test_suite(
  name = 'all',
  dependencies = [
    pants(':examples'),
  ]
)

python_test_suite(
  name = 'examples',
  dependencies = [
    pants(':test_pingpong_server'),
  ]
)

python_tests(
  name = 'test_pingpong_server',
  sources = ['test_pingpong_server.py'],
  dependencies = [
    pants('src/python/twitter/common/testing'),
    pants('src/python/twitter/common/examples:libpingpong'),
  ]
)
