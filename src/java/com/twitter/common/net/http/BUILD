java_library(name = 'http',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('3rdparty/jvm/com/google/inject/extensions:guice-servlet'),
    pants('3rdparty/jvm/org/mortbay/jetty'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/javax/servlet:servlet-api'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net/http/handlers:text'),
    pants('src/java/com/twitter/common/util:system-mocks'),
  ],
  sources = globs('*.java')
)
