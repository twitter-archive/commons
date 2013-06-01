python_library(
  name = 'common',
  sources = ['common.py'],
  dependencies = [
    pants('src/python/twitter/common/lang'),
  ]
)

python_library(
  name = 'iterators',
  sources = ['iterators.py'],
  dependencies = [
    pants('3rdparty/python:git-python'),
    pants('src/python/twitter/common/dirutil'),
  ]
)

python_library(
  name = 'checker',
  sources = ['checker.py'],
  dependencies = [
    pants(':common'),
    pants(':iterators'),
    pants('src/python/twitter/common/app'),
  ]
)

def make_checker(name, *plugins):
  python_binary(
    name = name,
    entry_point = 'twitter.checkstyle.checker',
    dependencies = [pants(':checker')] + [
        pants('src/python/twitter/checkstyle/plugins:%s' % plugin) for plugin in plugins
    ]
  )

make_checker('check_pep8', 'pep8')
make_checker('check_pyflakes', 'pyflakes')
make_checker('check_twitter', 'twitter')
make_checker('check', 'all')
