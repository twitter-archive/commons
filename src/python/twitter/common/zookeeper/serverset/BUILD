python_library(
  name = 'serverset_base',
  sources = globs('*.py', exclude = ['cli.py']),
  dependencies = [
    'src/python/twitter/common/lang',
    'src/python/twitter/common/zookeeper/group:group_base',
    'src/thrift/com/twitter/thrift:py-thrift',
    '3rdparty/python:thrift',
  ]
)

python_library(
  name = 'serverset',
  dependencies = [
    ':serverset_base',
    'src/python/twitter/common/zookeeper/group',
  ]
)

python_library(
  name = 'kazoo_serverset',
  dependencies = [
    ':serverset_base',
    'src/python/twitter/common/zookeeper/group:kazoo_group',
  ]
)

python_binary(
  name = 'cli',
  source = 'cli.py',
  dependencies = [
    'src/python/twitter/common/app',
    ':serverset'
  ],
)
