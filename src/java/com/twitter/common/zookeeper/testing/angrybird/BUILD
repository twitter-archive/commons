java_library(name = 'angrybird-lib',
  dependencies = [
    '3rdparty/jvm/org/apache/thrift',
    '3rdparty/jvm/org/apache/zookeeper',
    '3rdparty/jvm/com/twitter:finagle-http',
    '3rdparty/jvm/com/twitter:finagle-serversets',
    '3rdparty/jvm/com/twitter:finagle-thrift',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/log4j',
    'src/java/com/twitter/common/application',
    'src/java/com/twitter/common/application/http',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/zookeeper:testing',
    'src/thrift/com/twitter/common/zookeeper/testing/angrybird',
  ],
  sources = globs('*.java'),
)

jvm_binary(name = 'angrybird',
  main = 'com.twitter.common.zookeeper.testing.angrybird.AngryBirdZooKeeperMain',
  dependencies = [
    ':angrybird-lib',
  ]
)
