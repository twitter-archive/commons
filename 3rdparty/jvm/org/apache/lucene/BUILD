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

#
# Please update Lucene Version with caution. It could break several services, and affect
# the ability to load long-lived indexes stored on HDFS.
#

def make_lib(name, jar_name, rev, force=False):
  jar_library(name=name,
    jars=[
      jar(org='org.apache.lucene', name=jar_name, rev=rev, force=force)
    ]
  )

LUCENE_TARGETS = ('lucene-analyzers-common',
                  'lucene-analyzers-smartcn',
                  'lucene-core')

# Default targets without version numbers.
for name in LUCENE_TARGETS:
  make_lib(name=name, jar_name=name, rev='4.4.0')

# Make explicitly versioned targets
# Please do not delete existing targets, make new ones.
# This is important because we don't want these target to ever change without warning
# as it can cause long-lived indexes stored on disk or in HDFS to become unreadable.
def make_all_explicit(rev):
  for name in LUCENE_TARGETS:
    make_lib(name='%s-%s' % (name, rev), jar_name=name, rev=rev, force=True)

make_all_explicit('4.1.0')
make_all_explicit('4.4.0')

