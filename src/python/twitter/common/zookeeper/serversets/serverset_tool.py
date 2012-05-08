# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from twitter.common import app
from twitter.common.zookeeper.serversets import ServerSetClient


def _format_instances(instances):
  return ', '.join(
      '%s:%s' % (i.serviceEndpoint.host, i.serviceEndpoint.port)
      for i in instances)


def main(args, options):
  if not args:
    app.error('expected at least one ServerSet endpoint')

  def changed(endpoint, old, new):
    print '%s changed:' % endpoint
    print '  old:', _format_instances(old)
    print '  new:', _format_instances(new)
    print

  print 'Watching ServerSet endpoints. Hit ^C to exit.'
  print

  endpoints = []
  for arg in args:
    endpoints.append(ServerSetClient(arg, watcher=changed))
  while True:
    raw_input()


app.set_usage('%prog <endpoint> ...')
app.main()
