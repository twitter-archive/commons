python_library(
  name = 'sasl',
  sources = globs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/lang'),
    python_requirement('kerberos'),
    python_requirement('pure-sasl'),
    python_requirement('thrift'),
  ]
)
