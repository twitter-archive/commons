from pants import *

java_library(name = 'checkstyle',
  dependencies = [
    pants('3rdparty:antlr'),
    pants('3rdparty:checkstyle'),
    pants('3rdparty:commons-beanutils'),
    pants('3rdparty:guava'),
  ],
  sources = globs('*.java'),
  deployjar = True,
  buildflags = [ '-Dcompile.deploy-jar.name=checkstyle-custom-checks' ],
)
