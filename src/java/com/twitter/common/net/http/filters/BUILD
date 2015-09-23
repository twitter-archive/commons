java_library(name = 'filters',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-filters',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/com/sun/jersey:jersey-core',
    '3rdparty/jvm/com/sun/jersey:jersey-server',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/util',
  ],
  sources = globs('*.java')
)
