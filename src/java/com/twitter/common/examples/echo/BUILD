# This is part of the pants echo example. Please see:
# src/java/com/twitter/common/examples/echo/README.md

java_library(name='echo',
  dependencies=[
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/apache/hadoop:hadoop-core'),
  ],
  sources=globs('*.java'),
)

jvm_binary(name='echo-bin',
  dependencies=[
    pants(':echo'),
  ],
  main='com.twitter.common.examples.echo.EchoMain',
)
