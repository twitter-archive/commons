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

# test.py config magic to get a cmd-line argument passed through to our test.

def pytest_addoption(parser):
  parser.addoption("--generate_golden_data", action="store", default=None,
    help="If specified, write the generated output to this path.  Use this only when you're " \
         "convinced that the generated data is correct and the old golden data is not.")

