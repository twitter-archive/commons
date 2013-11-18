TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  sources = globs('*.py') - TEST_SERVER,
  dependencies = [
    python_requirement('zc-zookeeper-static'),
    pants('src/python/twitter/common')
  ],
)

python_library(
  name = 'kazoo_client',
  sources = ['kazoo_client.py'],
  dependencies = [
    pants('3rdparty/python:kazoo'),
    pants('src/python/twitter/common/metrics'),
  ]
)

python_library(
  name = 'testing',
  sources = TEST_SERVER,
  dependencies = [
    pants('src/python/twitter/common/contextutil'),
    pants('src/python/twitter/common/dirutil'),
    pants('src/python/twitter/common/rpc/finagle'),
    pants('src/thrift/com/twitter/common/zookeeper/testing/angrybird:py-thrift')
  ]
)

python_library(
  name = 'zookeeper-packaged',
  dependencies = [
    pants(':kazoo_client'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
    pants('src/python/twitter/common/zookeeper/serverset:kazoo_serverset'),
  ],
  provides = setup_py(
    name = 'twitter.common.zookeeper',
    version = '0.1.1',
    description = "Implementations of Twitter's service discovery libraries on top of Kazoo."
  )
)
