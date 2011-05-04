// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

namespace java com.twitter.thrift
namespace rb Twitter.Thrift
namespace py science.thrift

include "endpoint.thrift"

service ThriftService {

  /*
   * Gets a descriptive (unique) name for the service.
   */
  string getName()

  /**
   * Gets a version identifier string for the running build of the service.
   */
  string getVersion()

  /*
   * Gets the status of the service.
   */
  endpoint.Status getStatus()

  /*
   * Gets a human-readable description of the service health, including any
   * available details about non-healthy states.
   */
  string getStatusDetails()

  /*
   * Gets the counters associated with the service.
   */
  map<string, i64> getCounters()

  /*
   * Gets the value for a single counter.
   */
  i64 getCounter(1: string key)

  /*
   * Sets an option.
   */
  void setOption(1: string key, 2: string value)

  /*
   * Gets the value of an option.
   */
  string getOption(1: string key)

  /*
   * Gets the values of all options.
   */
  map<string, string> getOptions()

  /*
   * Gets the uptime of the service (in seconds).
   */
  i64 uptime()

  /*
   * Requests that a server initiate a shutdown.
   */
  oneway void shutdown()
}
