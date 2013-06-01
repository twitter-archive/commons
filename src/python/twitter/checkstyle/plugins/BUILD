python_library(
  name = 'all',
  dependencies = [
    pants(':pep8'),
    pants(':pyflakes'),
    pants(':twitter'),
  ]
)

python_library(
  name = 'pep8',
  sources = ['pep8.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:common'),
    python_requirement('pep8==1.4.5', name='pep8_module'),
  ]
)

python_library(
  name = 'pyflakes',
  sources = ['pyflakes.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:common'),
    python_requirement('pyflakes==0.7.2', name='pyflakes_module'),
  ]
)

python_library(
  name = 'twitter',
  sources = globs('*.py') - ['pep8.py', 'pyflakes.py'],
  dependencies = [
    pants('src/python/twitter/checkstyle:common'),
  ]
)
