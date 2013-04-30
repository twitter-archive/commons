java_library(name = 'pprof',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-pprof',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:util-core'),
    pants('3rdparty:util-jvm'),
    pants('src/java/com/twitter/common/net/http/handlers:params'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = globs('*.java')
)
