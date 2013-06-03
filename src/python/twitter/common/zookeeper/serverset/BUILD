python_library(
  name = 'serverset_base',
  sources = globs('*.py') - ['cli.py'],
  dependencies = [
    pants('src/python/twitter/common/lang'),
    pants('src/python/twitter/common/zookeeper/group:group_base'),
    pants('src/thrift/com/twitter/thrift:py-thrift'),
  ]
)

python_library(
  name = 'serverset',
  dependencies = [
    pants(':serverset_base'),
    pants('src/python/twitter/common/zookeeper/group'),
    pants('src/python/twitter/common/zookeeper'),
  ]
)

python_library(
  name = 'kazoo_serverset',
  dependencies = [
    pants(':serverset_base'),
    pants('src/python/twitter/common/zookeeper/group:kazoo_group'),
  ]
)

python_binary(
  name = 'cli',
  source = 'cli.py',
  dependencies = [
    pants('src/python/twitter/common/app'),
    pants(':serverset')
  ],
)
