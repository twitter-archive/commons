scalac_plugin(name='dependency-emitter',
  plugin='depemitter',
  classname='com.twitter.common.tools.compiler.plugins.DependencyEmitter',
  provides=artifact(
    org = 'com.twitter.common.tools',
    name = 'dependency-emitter-plugin_2.9.3',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies=[
    '3rdparty/jvm/org/scala-lang:scala-compiler',
  ],
  sources=['plugins/DependencyEmitter.scala']
)
