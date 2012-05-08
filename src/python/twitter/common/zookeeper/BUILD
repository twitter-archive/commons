TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  sources = globs('*.py') - TEST_SERVER,
  dependencies = [
    pants('3rdparty/python:zookeeper'),
    pants('src/python/twitter/common/lang'),
  ],
)

python_library(
  name = 'testing',
  sources = TEST_SERVER,
  dependencies = [
    pants(':zookeeper'),
    pants('src/python/twitter/common/dirutil'),
    pants('src/python/twitter/common/rpc/finagle'),
    pants('src/thrift/com/twitter/common/zookeeper/testing/angrybird:py-thrift')
  ]
)
