TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  sources = globs('*.py') - TEST_SERVER,
  dependencies = [
    '3rdparty/python:zookeeper',
    'src/python/twitter/common/lang',
  ],
)

python_library(
  name = 'testing',
  sources = TEST_SERVER,
  dependencies = [
    ':zookeeper',
    'src/python/twitter/common/dirutil',
    'src/python/twitter/common/rpc/finagle',
    'src/thrift/com/twitter/common/zookeeper/testing/angrybird:py-thrift'
  ]
)
