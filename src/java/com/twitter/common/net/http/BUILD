java_library(name = 'http',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/com/google/inject/extensions:guice-servlet',
    '3rdparty/jvm/org/mortbay/jetty',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/javax/servlet:servlet-api',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net/http/handlers:text',
    'src/java/com/twitter/common/util:system-mocks',
  ],
  sources = globs('*.java')
)
