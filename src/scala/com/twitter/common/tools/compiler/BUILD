scalac_plugin(name='dependency-emitter',
  plugin='depemitter',
  classname='com.twitter.common.tools.compiler.plugins.DependencyEmitter',
  provides=artifact(
    org = 'com.twitter.common.tools',
    name = 'dependency-emitter-plugin_2.8.1',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies=[
    '3rdparty:scala-compiler',
  ],
  sources=['plugins/DependencyEmitter.scala']
)
