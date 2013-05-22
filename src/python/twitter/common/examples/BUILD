python_library(
  name = 'libpingpong',
  sources = ['pingpong.py'],
  dependencies = [
    pants('src/python/twitter/common/concurrent'),
    pants('src/python/twitter/common/http'),
    pants('src/python/twitter/common/log'),
    pants('src/python/twitter/common/metrics'),
  ]
)

python_binary(
  name = 'pingpong',
  source = 'pingpong_main.py',
  dependencies = [
    pants('src/python/twitter/common/app'),
    pants('src/python/twitter/common/app/modules:exceptions'),
    pants('src/python/twitter/common/app/modules:http'),
    pants('src/python/twitter/common/app/modules:vars'),
    pants(':libpingpong'),
  ]
)
