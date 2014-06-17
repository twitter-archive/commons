# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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

import unittest

from twitter.common.app.modules.varz import VarsEndpoint, VarsSubsystem
from twitter.common.http.server import request
from twitter.common.quantity import Amount, Time
from twitter.common.metrics import NamedGauge, RootMetrics

import json
import pytest


class TestVarz(unittest.TestCase):
  def test_breaking_out_regex(self):
    vars_subsystem = VarsSubsystem()
    regex = vars_subsystem.compile_stats_filters(["alpha", "beta.*"])
    assert regex.match("alpha")
    assert not regex.match("something_alpha_something")
    assert regex.match("beta")
    assert regex.match("beta_suffix")
    assert not regex.match("abeta")

  def test_filtering_vars_filter_enabled_and_requested(self):
    rm = RootMetrics()
    zone = NamedGauge('alpha', "wont_be_visible")
    alpha = NamedGauge('zone', "smf1")
    rm.register(zone)
    rm.register(alpha)
    metrics = RootMetrics()
    vars_subsystem = VarsSubsystem()
    regex = vars_subsystem.compile_stats_filters(["alpha", "beta.*"])
    endpoint = VarsEndpoint(period=Amount(60000, Time.MILLISECONDS), stats_filter=regex)
    request.GET.append('filtered', '1')
    metrics_returned = endpoint.handle_vars_json()
    assert "zone" in metrics_returned
    assert "alpha" not in metrics_returned
    request.GET.replace('filtered', None)

  def test_filtering_vars_filter_enabled_and_not_requested(self):
    rm = RootMetrics()
    zone = NamedGauge('alpha', "wont_be_visible")
    alpha = NamedGauge('zone', "smf1")
    rm.register(zone)
    rm.register(alpha)

    metrics = RootMetrics()
    vars_subsystem = VarsSubsystem()
    regex = vars_subsystem.compile_stats_filters(["alpha", "beta.*"])
    endpoint = VarsEndpoint(period=Amount(60000, Time.MILLISECONDS), stats_filter=regex)
    metrics_returned = endpoint.handle_vars_json()
    assert "zone" in metrics_returned
    assert "alpha" in metrics_returned
    request.GET.replace('filtered', None)

  def test_filtering_vars_filter_disabled_and_requested(self):
    rm = RootMetrics()
    zone = NamedGauge('alpha', "wont_be_visible")
    alpha = NamedGauge('zone', "smf1")
    rm.register(zone)
    rm.register(alpha)

    metrics = RootMetrics()
    vars_subsystem = VarsSubsystem()
    regex = None
    endpoint = VarsEndpoint(period=Amount(60000, Time.MILLISECONDS), stats_filter=regex)
    request.GET.append('filtered', '1')
    metrics_returned = endpoint.handle_vars_json()
    assert "zone" in metrics_returned
    assert "alpha" in metrics_returned
    request.GET.replace('filtered', None)