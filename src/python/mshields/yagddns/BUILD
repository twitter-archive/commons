python_binary(
  name='yagddns',
  source='yagddns.py',
  dependencies=[
    '3rdparty/python:pyyaml',
    'src/python/mshields/yagddns:lib',
    'src/python/twitter/common/app:app',
    'src/python/twitter/common/log:log',
  ],
)

python_library(
  name='lib',
  sources=['lib.py'],
  dependencies=[
    '3rdparty/python:requests',
  ],
)
