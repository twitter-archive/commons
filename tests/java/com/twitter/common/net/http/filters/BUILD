java_tests(name = 'filters',
  dependencies = [
    pants('3rdparty:easymock'),
    pants('3rdparty:guice'),
    pants('3rdparty:junit'),
    pants('3rdparty/jvm/com/sun/jersey:jersey-guice'),
    pants('3rdparty/jvm/com/sun/jersey:jersey-client'),
    pants('3rdparty/jvm/com/sun/jersey:jersey-servlet'),
    pants('src/java/com/twitter/common/net/http'),
    pants('src/java/com/twitter/common/net/http/filters'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/util:testing'),
  ],
  sources = globs('*.java')
)
