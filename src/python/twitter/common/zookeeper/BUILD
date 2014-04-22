with open(os.path.join(os.path.dirname(__file__), '..', 'VERSION')) as fp:
  VERSION = fp.read().strip()

TEST_SERVER = ['test_server.py']

python_library(
  name = 'zookeeper',
  dependencies = [
    pants(':kazoo_client'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
    pants('src/python/twitter/common/zookeeper/serverset:kazoo_serverset'),

    # keep around zookeeper-old code until we can purge it forever.  do not use
    # these implementations!  use the kazoo implementations!
    pants(':zookeeper-old'),
    pants('src/python/twitter/common/zookeeper/group'),
    pants('src/python/twitter/common/zookeeper/serverset'),
  ],
  provides = setup_py(
    name = 'twitter.common.zookeeper',
    version = VERSION,
    description = "Implementations of Twitter's service discovery libraries on top of Kazoo.",
    extras_require = {
      # Require twitter.common.zookeeper[old] to pull in zc-zookeeper-static, which will not
      # come by default.  This should be a stopgap until we can remove it completely.
      'old': [
        'zc-zookeeper-static==3.4.4',
      ],
    },
  )
)

python_library(
  name = 'zookeeper-old',
  sources = ['client.py', 'constants.py', 'named_value.py'],
  dependencies = [
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/metrics'),
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
