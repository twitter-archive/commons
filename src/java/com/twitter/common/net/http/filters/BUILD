java_library(name = 'filters',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-filters',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:servlet-api'),
  ],
  sources = globs('*.java')
)
