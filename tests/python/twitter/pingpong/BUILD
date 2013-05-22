python_test_suite(
  name = 'all',
  dependencies = [
    pants(':pingpong')
  ]
)

python_test_suite(
  name = 'pingpong',
  dependencies = [
    pants(':test_pingpong_server')
  ]
)

python_tests(
  name = 'test_pingpong_server',
  sources = ['test_pingpong_server.py'],
  dependencies = [
    pants('src/python/twitter/common/testing'),
    pants('src/python/twitter/pingpong:libpingpong'),
  ]
)
