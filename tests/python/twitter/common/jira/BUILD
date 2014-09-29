python_tests(name = 'jira',
  sources = ['test_jira.py'],
  dependencies = [
    'src/python/twitter/common/jira',
    '3rdparty/python:mock'
  ]
)

python_test_suite(name = 'all',
  dependencies = [
    ':jira',
  ]
)
