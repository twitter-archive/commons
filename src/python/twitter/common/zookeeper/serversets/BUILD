python_library(
  name = 'serversets',
  sources = ['__init__.py', 'client.py'],
  dependencies = [
    '3rdparty/python:zookeeper',
    'src/python/twitter/common/app',
    'src/python/twitter/common/log',
    'src/python/twitter/common/quantity',
    'src/python/twitter/common/zookeeper',
    'src/thrift/com/twitter/thrift:py-thrift',
  ],
)

python_binary(
  name = 'serverset',
  source = 'serverset_tool.py',
  dependencies = [
    ':serversets',
  ],
)
