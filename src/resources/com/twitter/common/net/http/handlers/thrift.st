<!--
=================================================================================================
Copyright 2011 Twitter, Inc.
-------------------------------------------------------------------------------------------------
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
=================================================================================================
-->

<html>
  <body>
    <h2>Clients</h2>
    $if(clientMonitors)$
      $clientMonitors:{ monitor |
        <div>
          <h3>$monitor.serviceName$</h3>
          Lifetime Requests: $monitor.lifetimeRequestCount$
          <br />
          <table border=0>
            <tr>
              <th>Server</th>
              <th>Leased Connections</th>
              <th>Successful Requests</th>
              <th>Failed Requests</th>
            </tr>
            $monitor.trafficInfo:{ trafficInfo |
              <tr>
                <td align='center'>$trafficInfo.key$</td>
                <td align='center'>$trafficInfo.connectionCount$</td>
                <td align='center'>$trafficInfo.requestSuccessCount$</td>
                <td align='center'>$trafficInfo.requestFailureCount$</td>
              </tr>
            }$
          </table>
        </div>
      }$
    $else$
      No thrift clients registered.
    $endif$

    <br />
    <br />

    <h2>Servers</h2>
    $if(serverMonitors)$
      $serverMonitors:{ monitor |
        <div>
          <h3>$monitor.serviceName$</h3>
          Lifetime Requests: $monitor.lifetimeRequestCount$
          <br />
          <table border=0>
            <tr>
              <th>Client</th>
              <th>Active Connections</th>
              <th>Successful Requests</th>
              <th>Failed Requests</th>
            </tr>
            $monitor.trafficInfo:{ trafficInfo |
              <tr>
                <td align='center'>$trafficInfo.key$</td>
                <td align='center'>$trafficInfo.connectionCount$</td>
                <td align='center'>$trafficInfo.requestSuccessCount$</td>
                <td align='center'>$trafficInfo.requestFailureCount$</td>
              </tr>
            }$
          </table>
        </div>
      }$
    $else$
      No thrift servers registered.
    $endif$
  </body>
</html>
