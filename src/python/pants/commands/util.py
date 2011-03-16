# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license
# agreements.  See the NOTICE file distributed with this work for additional information regarding
# copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied.  See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

"""Utility methods shared by various pants subcommands."""

__author__ = 'John Sirios'

from common.collections import OrderedSet
from pants import (
  BuildFile,
  Target
)

def scan_addresses(root_dir, base_path = None):
  """Parses all targets available in BUILD files under base_path and returns their addresses.  If no
  base_path is specified, root_dir is assumed to be the base_path"""

  addresses = OrderedSet()
  for buildfile in BuildFile.scan_buildfiles(base_path if base_path else root_dir):
    addresses.update(Target.get_all_addresses(buildfile))
  return addresses

