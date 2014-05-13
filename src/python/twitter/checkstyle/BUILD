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
    pants('src/python/twitter/checkstyle/plugins:plugin'),
    pants('src/python/twitter/common/app'),
  ]
)

def make_checker(name, *plugins):
  python_binary(
    name = name,
    entry_point = 'twitter.checkstyle.checker:proxy_main',
    dependencies = [pants(':checker')] + [
        pants('src/python/twitter/checkstyle/plugins:%s' % plugin) for plugin in plugins
    ]
  )

make_checker('check_pep8', 'pep8')
make_checker('check_pyflakes', 'pyflakes')
make_checker('check_twitter', 'twitter')
make_checker('check', 'all')


python_library(
  name = 'checkstyle',
  dependencies = [
    pants(':checker'),
  ],
  provides = setup_py(
    name = 'twitter.checkstyle',
    version = '0.1.0',
    description = "Twitter's python styleguide checkstyle tool.",
    url = 'https://github.com/twitter/commons/tree/master/src/python/twitter/checkstyle',
    license = 'Apache License, Version 2.0',
    zip_safe = True,
  ).with_binaries(
    twitterstyle = pants(':check'),
  )
)
