python_library(
  name = 'serversets',
  sources = ['__init__.py', 'client.py'],
  dependencies = [
    pants('3rdparty/python:zookeeper'),
    pants('src/python/twitter/common/app'),
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/quantity'),
    pants('src/python/twitter/common/zookeeper'),
    pants('src/thrift/com/twitter/thrift:py-thrift'),
  ],
)

python_binary(
  name = 'serverset',
  source = 'serverset_tool.py',
  dependencies = [
    pants(':serversets'),
  ],
)
