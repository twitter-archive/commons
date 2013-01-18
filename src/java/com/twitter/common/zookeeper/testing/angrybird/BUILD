java_library(name = 'angrybird-lib',
  dependencies = [
    '3rdparty:finagle-http',
    '3rdparty:finagle-serversets',
    '3rdparty:finagle-thrift',
    '3rdparty:guice',
    '3rdparty:thrift',
    '3rdparty:zookeeper',
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
