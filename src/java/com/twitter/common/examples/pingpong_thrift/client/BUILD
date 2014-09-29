java_library(name='client',
  dependencies=[
    'src/java/com/twitter/common/application',
    'src/thrift/com/twitter/common/examples/pingpong',
  ],
  sources=globs('*.java'),
)

jvm_binary(name='client-bin',
  dependencies=[':client'],
  main='com.twitter.common.examples.pingpong_thrift.client.PingPongClient',
)
