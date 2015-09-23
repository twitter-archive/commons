java_library(name='server',
  dependencies=[
    'src/java/com/twitter/common/application',
    'src/thrift/com/twitter/common/examples/pingpong',
  ],
  sources=globs('*.java'),
)

jvm_binary(name='server-bin',
  dependencies=[':server'],
  main='com.twitter.common.examples.pingpong_thrift.server.PingPongServer',
)
