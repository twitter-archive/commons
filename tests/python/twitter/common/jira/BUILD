python_tests(name = 'jira',
  sources = ['test_jira.py'],
  dependencies = [
    'src/python/twitter/common/jira',
    '3rdparty/python:mock'
  ]
)

target(name = 'all',
  dependencies = [
    ':jira',
  ]
)
