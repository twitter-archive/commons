java_library(name = 'angrybird-lib',
  dependencies = [
    pants('3rdparty/jvm/org/apache/thrift'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('3rdparty/jvm/com/twitter:finagle-http'),
    pants('3rdparty/jvm/com/twitter:finagle-serversets'),
    pants('3rdparty/jvm/com/twitter:finagle-thrift'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('3rdparty/jvm/log4j'),
    pants('src/java/com/twitter/common/application'),
    pants('src/java/com/twitter/common/application/http'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/thrift/com/twitter/common/zookeeper/testing/angrybird'),
  ],
  sources = globs('*.java'),
)

jvm_binary(name = 'angrybird',
  main = 'com.twitter.common.zookeeper.testing.angrybird.AngryBirdZooKeeperMain',
  dependencies = [
    pants(':angrybird-lib'),
  ]
)
