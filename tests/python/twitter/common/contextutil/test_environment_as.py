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

import sys
import subprocess
from twitter.common.contextutil import environment_as, temporary_file

def test_empty_environment():
  with environment_as():
    pass

def test_override_single_variable():
  with temporary_file() as output:
    # test that the override takes place
    with environment_as(HORK = 'BORK'):
      subprocess.Popen([sys.executable, '-c', 'import os; print(os.environ["HORK"])'],
        stdout=output).wait()
      output.seek(0)
      assert output.read() == 'BORK\n'

    # test that the variable is cleared
    with temporary_file() as new_output:
      subprocess.Popen([sys.executable, '-c', 'import os; print("HORK" in os.environ)'],
        stdout=new_output).wait()
      new_output.seek(0)
      assert new_output.read() == 'False\n'

def test_environment_negation():
  with temporary_file() as output:
    with environment_as(HORK = 'BORK'):
      with environment_as(HORK = None):
      # test that the variable is cleared
        subprocess.Popen([sys.executable, '-c', 'import os; print("HORK" in os.environ)'],
          stdout=output).wait()
        output.seek(0)
        assert output.read() == 'False\n'
