java_library(name='client',
  dependencies=[
    pants('src/java/com/twitter/common/application'),
    pants('src/thrift/com/twitter/common/examples/pingpong'),
  ],
  sources=globs('*.java'),
)

jvm_binary(name='client-bin',
  dependencies=[pants(':client')],
  main='com.twitter.common.examples.pingpong_thrift.client.PingPongClient',
)
