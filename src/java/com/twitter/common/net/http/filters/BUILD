java_library(name = 'filters',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-filters',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:servlet-api'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = globs('*.java')
)
