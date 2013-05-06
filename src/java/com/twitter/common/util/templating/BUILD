java_library(name = 'templating',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-templating',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:stringtemplate'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = globs('*.java')
)
