python_library(
  name = 'zookeeper',
  sources = ['__init__.py', 'client.py', 'port_allocator.py', 'test_cluster.py'],
  dependencies = [
    pants('3rdparty/python:zookeeper'),
    pants('src/python/twitter/common/app'),
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/quantity'),
  ],
)
