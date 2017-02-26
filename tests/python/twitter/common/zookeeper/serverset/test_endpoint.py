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

import pytest
from twitter.common.zookeeper.serverset.endpoint import Endpoint, ServiceInstance, Status


def test_endpoint_constructor():
  # Check that those do not throw
  Endpoint('host', 8340)
  Endpoint('host', 8340, '1.2.3.4')
  Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')
  Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')

  with pytest.raises(ValueError):
    Endpoint('host', 8340, 'not an IP')
  with pytest.raises(ValueError):
    Endpoint('host', 8340, None, 'not an IPv6')


def test_endpoint_equality():
  assert Endpoint('host', 8340) == Endpoint('host', 8340)
  assert Endpoint('host', 8340, '1.2.3.4') == Endpoint('host', 8340, '1.2.3.4')
  assert (Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')
          == Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff'))
  assert (Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')
          == Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff'))


def test_endpoint_hash_equality():
  assert Endpoint('host', 8340).__hash__() == Endpoint('host', 8340).__hash__()
  assert Endpoint('host', 8340, '1.2.3.4').__hash__() == Endpoint('host', 8340, '1.2.3.4').__hash__()
  assert (Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__()
          == Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__())
  assert (Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__()
          == Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__())


def test_endpoint_inequality():
  assert Endpoint('host', 8340) != Endpoint('xhost', 8340)
  assert Endpoint('host', 8340) != Endpoint('host', 8341)
  assert (Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')
          != Endpoint('host', 8340, '5.6.7.8', '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'))
  assert (Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff')
          != Endpoint('host', 8340, None, '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'))


def test_endpoint_hash_inequality():
  assert Endpoint('host', 8340).__hash__() != Endpoint('xhost', 8341).__hash__()
  assert Endpoint('host', 8340).__hash__() != Endpoint('host', 8341).__hash__()
  assert (Endpoint('host', 8340, '1.2.3.4', '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__()
          != Endpoint('host', 8340, '5.6.7.8', '2001:db8:5678:ffff:ffff:ffff:ffff:ffff').__hash__())
  assert (Endpoint('host', 8340, None, '2001:db8:1234:ffff:ffff:ffff:ffff:ffff').__hash__()
          != Endpoint('host', 8340, None, '2001:db8:5678:ffff:ffff:ffff:ffff:ffff').__hash__())


def test_endpoint_from_dict():
  expected = {
    Endpoint('smfd-akb-12-sr1', 31181): {'host': 'smfd-akb-12-sr1', 'port': 31181},
    Endpoint('smfd-akb-12-sr1', 31181, '1.2.3.4'): {'host': 'smfd-akb-12-sr1', 'port': 31181, 'inet': '1.2.3.4'},
    Endpoint('smfd-akb-12-sr1', 31181, '1.2.3.4', '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'):
      {'host': 'smfd-akb-12-sr1', 'port': 31181, 'inet': '1.2.3.4', 'inet6':
       '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'},
    Endpoint('smfd-akb-12-sr1', 31181, None, '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'):
      {'host': 'smfd-akb-12-sr1', 'port': 31181, 'inet6': '2001:db8:5678:ffff:ffff:ffff:ffff:ffff'}
  }
  for (endpoint, dic) in expected.items():
    assert Endpoint.to_dict(endpoint) == dic
    assert Endpoint.from_dict(dic) == endpoint


def test_status_equality():
  assert Status.from_string('DEAD') == Status.from_string('DEAD')


def test_status_hash_equality():
  assert Status.from_string('DEAD').__hash__() == Status.from_string('DEAD').__hash__()


def test_status_inequality():
  assert Status.from_string('DEAD') != Status.from_string('STARTING')


def test_status_hash_inequality():
  assert Status.from_string('DEAD').__hash__() != Status.from_string('STARTING').__hash__()


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


def test_service_instance_to_json():
  json = """{
    "additionalEndpoints": {
        "aurora": {
            "host": "hostname",
            "inet6": "2001:db8:1234:ffff:ffff:ffff:ffff:ffff",
            "port": 22
        },
        "health": {
            "host": "hostname",
            "inet": "1.2.3.4",
            "port": 23
        },
        "http": {
            "host": "hostname",
            "inet": "1.2.3.4",
            "inet6": "2001:db8:1234:ffff:ffff:ffff:ffff:ffff",
            "port": 23
        }
    },
    "serviceEndpoint": {
        "host": "hostname",
        "port": 24
    },
    "shard": 1,
    "status": "ALIVE"
  }"""
  service_instance = ServiceInstance(
    Endpoint("hostname", 24),
    {"aurora": Endpoint("hostname", 22, "1.2.3.4"),
     "health": Endpoint("hostname", 23, None, "2001:db8:1234:ffff:ffff:ffff:ffff:ffff"),
     "http": Endpoint("hostname", 23, "1.2.3.4", "2001:db8:1234:ffff:ffff:ffff:ffff:ffff"),
   },
    'ALIVE',
    1
  )

  assert ServiceInstance.unpack(json) == service_instance
  assert ServiceInstance.unpack(ServiceInstance.pack(service_instance)) == service_instance
