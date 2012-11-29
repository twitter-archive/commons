java_tests(name = 'angrybird',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:hamcrest-core'),
    pants('3rdparty:junit'),
    pants('src/java/com/twitter/common/testing:action'),
    pants('src/java/com/twitter/common/zookeeper/testing/angrybird:angrybird-lib'),
    pants('src/java/com/twitter/common/zookeeper:client'),
  ],
  sources = rglobs('*.java'),
)
