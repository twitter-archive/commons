TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  sources = globs('*.py') - TEST_SERVER,
  dependencies = [
    python_requirement('zc-zookeeper-static'),
    pants('src/python/twitter/common')
  ],
  provides = setup_py(
    name = 'twitter.common.zookeeper',
    version = '0.0.0',
    description = "Twitter's reliable Zookeeper wrapper."
  )
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
