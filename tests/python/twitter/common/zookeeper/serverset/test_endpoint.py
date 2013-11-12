# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

from twitter.common.zookeeper.serverset.endpoint import Endpoint, ServiceInstance, Status


def _service_instance(vals):
  json = '''{
    "additionalEndpoints": {
        "aurora": {
            "host": "smfd-akb-%d-sr1.devel.twitter.com",
            "port": 31181
        },
        "health": {
            "host": "smfd-akb-%d-sr1.devel.twitter.com",
            "port": 31181
        }
    },
    "serviceEndpoint": {
        "host": "smfd-akb-%d-sr1.devel.twitter.com",
        "port": 31181
    },
    "shard": %d,
    "status": "ALIVE"
}''' % vals

  return ServiceInstance.unpack(json)


def test_endpoint_equality():
  assert Endpoint('host', 8340) == Endpoint('host', 8340)


def test_endpoint_hash_equality():
  assert Endpoint('host', 8340).__hash__() == Endpoint('host', 8340).__hash__()


def test_endpoint_inequality():
  assert Endpoint('host', 8340) != Endpoint('xhost', 8341)


def test_endpoint_hash_inequality():
  assert Endpoint('host', 8340).__hash__() != Endpoint('xhost', 8341).__hash__()


def test_status_equality():
  assert Status.from_string('DEAD') == Status.from_string('DEAD')


def test_status_hash_equality():
  assert Status.from_string('DEAD').__hash__() == Status.from_string('DEAD').__hash__()


def test_status_inequality():
  assert Status.from_string('DEAD') != Status.from_string('STARTING')


def test_status_hash_inequality():
  assert Status.from_string('DEAD').__hash__() != Status.from_string('STARTING').__hash__()


def test_service_instance_equality():
  vals = (1, 2, 3, 4)
  assert _service_instance(vals) == _service_instance(vals)


def test_service_instance_hash_equality():
  vals = (1, 2, 3, 4)
  assert _service_instance(vals).__hash__() == _service_instance(vals).__hash__()


def test_service_instance_inequality():
  vals = (1, 2, 3, 4)
  vals2 = (5, 6, 7, 8)
  assert _service_instance(vals) != _service_instance(vals2)


def test_service_instance_hash_inequality():
  vals = (1, 2, 3, 4)
  vals2 = (5, 6, 7, 8)
  assert _service_instance(vals).__hash__() != _service_instance(vals2).__hash__()
