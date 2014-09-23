python_library(
  name = 'libpingpong',
  sources = ['pingpong.py'],
  dependencies = [
    'src/python/twitter/common/concurrent',
    'src/python/twitter/common/http',
    'src/python/twitter/common/log',
    'src/python/twitter/common/metrics',
  ]
)

python_binary(
  name = 'pingpong',
  source = 'pingpong_main.py',
  dependencies = [
    'src/python/twitter/common/app',
    'src/python/twitter/common/app/modules:exceptions',
    'src/python/twitter/common/app/modules:http',
    'src/python/twitter/common/app/modules:vars',
    ':libpingpong',
  ]
)
