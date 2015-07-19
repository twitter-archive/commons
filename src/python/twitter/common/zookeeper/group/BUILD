python_library(
  name = 'group_base',
  sources = ['group_base.py'],
  dependencies = [
    'src/python/twitter/common/concurrent',
    'src/python/twitter/common/lang',
  ]
)

python_library(
  name = 'group',
  sources = globs('*.py', exclude = ['kazoo_group.py']),
  dependencies = [
    ':group_base',
    'src/python/twitter/common/concurrent',
    'src/python/twitter/common/exceptions',
    'src/python/twitter/common/quantity',
    'src/python/twitter/common/zookeeper:zookeeper-old',
  ]
)

python_library(
  name = 'kazoo_group',
  sources = ['kazoo_group.py'],
  dependencies = [
    ':group_base',
    'src/python/twitter/common/concurrent',
    'src/python/twitter/common/zookeeper:kazoo_client',
  ]
)

python_binary(
  name = 'kazoo_cli',
  source = 'kazoo_cli.py',
  dependencies = [
    'src/python/twitter/common/app',
    ':kazoo_group',
  ]
)
