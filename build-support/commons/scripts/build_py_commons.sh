PROJECTS=(
  app
  collections
  concurrent
  config
  confluence
  contextutil
  decorators
  dirutil
  exceptions
  fs
  git
  http
  java
  jira
  lang
  log
  metrics
  net
  options
  process
  quantity
  recordio:recordio-packaged
  resourcepool
  reviewboard
  rpc:rpc-packaged
  rwbuf
  string
  testing
  threading
  util
  zookeeper
)

for project in ${PROJECTS[@]}; do
  ./pants setup_py src/python/twitter/common/$project
done
