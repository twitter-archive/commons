java_library(name = 'filters',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'net-http-filters',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('3rdparty/jvm/javax/servlet:servlet-api'),
    pants('3rdparty/jvm/com/sun/jersey:jersey-core'),
    pants('3rdparty/jvm/com/sun/jersey:jersey-server'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = globs('*.java')
)
