python_library(
  name = 'serverset',
  sources = globs('*.py') - ['cli.py'],
  dependencies = [
    pants('src/python/twitter/common/lang'),
    pants('src/python/twitter/common/zookeeper/group'),
    pants('src/thrift/com/twitter/thrift:py-thrift'),
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
