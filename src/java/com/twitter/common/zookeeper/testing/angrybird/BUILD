java_library(name = 'angrybird-lib',
  dependencies = [
    pants('3rdparty:thrift'),
    pants('3rdparty:zookeeper'),
    pants('3rdparty:finagle-http'),
    pants('3rdparty:finagle-serversets'),
    pants('3rdparty:finagle-thrift'),
    pants('3rdparty:guice'),
    pants('3rdparty:log4j'),
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
    ':angrybird-lib',
  ]
)
