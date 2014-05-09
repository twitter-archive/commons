java_library(name = 'pprof',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-pprof',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/javax/servlet:servlet-api'),
    pants('3rdparty/jvm/com/twitter:util-core'),
    pants('3rdparty/jvm/com/twitter:util-jvm'),
    pants('src/java/com/twitter/common/net/http/handlers:params'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = globs('*.java')
)
