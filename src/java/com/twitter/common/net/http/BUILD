java_library(name = 'http',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:guice-servlet'),
    pants('3rdparty:jetty'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:servlet-api'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net/http/handlers:text'),
    pants('src/java/com/twitter/common/util:system-mocks'),
  ],
  sources = globs('*.java')
)
