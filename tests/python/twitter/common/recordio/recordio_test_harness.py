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

import os
import tempfile
from contextlib import contextmanager

try:
  from cStringIO import StringIO
except ImportError:
  from StringIO import StringIO


@contextmanager
def DurableFile(mode):
  fn = tempfile.mktemp()
  with open(fn, 'w'):
    pass
  with open(fn, mode) as fp:
    yield fp


@contextmanager
def EphemeralFile(mode):
  with DurableFile(mode) as fp:
    fn = fp.name
    yield fp
  os.remove(fn)
