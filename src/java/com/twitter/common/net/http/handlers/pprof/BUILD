java_library(name = 'pprof',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-handlers-pprof',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/com/twitter:util-core',
    '3rdparty/jvm/com/twitter:util-jvm',
    'src/java/com/twitter/common/net/http/handlers:params',
    'src/java/com/twitter/common/quantity',
  ],
  sources = globs('*.java')
)
