java_library(name = 'pingpong-lib',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    jar(org = 'com.sun.jersey',          name = 'jersey-client',  rev = 1.12).with_sources(),
    jar(org = 'com.sun.jersey',          name = 'jersey-core',    rev = 1.12).with_sources(),
    jar(org = 'com.sun.jersey.contribs', name = 'jersey-guice',   rev = 1.12).with_sources(),
    jar(org = 'com.sun.jersey',          name = 'jersey-server',  rev = 1.12).with_sources(),
    jar(org = 'com.sun.jersey',          name = 'jersey-servlet', rev = 1.12).with_sources(),
    pants('src/java/com/twitter/common/application'),
    pants('src/java/com/twitter/common/application/http'),
    pants('src/java/com/twitter/common/application/modules:http'),
    pants('src/java/com/twitter/common/application/modules:lifecycle'),
    pants('src/java/com/twitter/common/application/modules:log'),
    pants('src/java/com/twitter/common/application/modules:stats'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/stats'),
  ],
  sources = globs('*.java')
)

jvm_binary(name='pingpong',
  basename='pingpong',
  main='com.twitter.common.examples.pingpong.Main',
  dependencies=[
    pants(':pingpong-lib')
  ]
)
