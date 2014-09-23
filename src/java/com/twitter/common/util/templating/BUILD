java_library(name = 'templating',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-templating',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/antlr:stringtemplate',
    'src/java/com/twitter/common/base',
  ],
  sources = globs('*.java')
)
