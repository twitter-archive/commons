python_library(
  name = 'group',
  sources = globs('*.py') - ['kazoo_group.py'],
  dependencies = [
    pants('src/python/twitter/common/concurrent'),
    pants('src/python/twitter/common/quantity'),
    pants('src/python/twitter/common/zookeeper'),
  ]
)

python_library(
  name = 'kazoo_group',
  sources = ['kazoo_group.py'],
  dependencies = [
    pants(':group'),
    # TODO(wickman) Update this to kazoo==1.2 once they cut a release
    # with our patches.
    python_requirement('kazoo==1.1dev'),
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
