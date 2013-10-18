java_library(name='server',
  dependencies=[
    pants('src/java/com/twitter/common/application'),
    pants('src/thrift/com/twitter/common/examples/pingpong'),
  ],
  sources=globs('*.java'),
)

jvm_binary(name='server-bin',
  dependencies=[pants(':server')],
  main='com.twitter.common.examples.pingpong_thrift.server.PingPongServer',
)
