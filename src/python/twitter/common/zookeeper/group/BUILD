python_library(
  name = 'group_base',
  sources = ['group_base.py'],
  dependencies = [
    pants('src/python/twitter/common/concurrent'),
    pants('src/python/twitter/common/lang'),
  ]
)

python_library(
  name = 'group',
  sources = globs('*.py') - ['kazoo_group.py'],
  dependencies = [
    pants(':group_base'),
    pants('src/python/twitter/common/concurrent'),
    pants('src/python/twitter/common/exceptions'),
    pants('src/python/twitter/common/quantity'),
    pants('src/python/twitter/common/zookeeper:zookeeper-old'),
  ]
)

python_library(
  name = 'kazoo_group',
  sources = ['kazoo_group.py'],
  dependencies = [
    pants(':group_base'),
    pants('src/python/twitter/common/concurrent'),
    pants('src/python/twitter/common/zookeeper:kazoo_client'),
  ]
)

python_binary(
  name = 'kazoo_cli',
  source = 'kazoo_cli.py',
  dependencies = [
    pants('src/python/twitter/common/app'),
    pants(':kazoo_group'),
  ]
)
