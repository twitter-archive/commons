# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

import threading
from functools import wraps


def __gettid():
  """Wrapper for the gettid() system call on Linux systems

  This is a lightweight, fail-fast function to obtain the thread ID of the thread from which it is
  called.  Unfortunately, there's not currently any means of obtaining this information on Linux
  other than through a direct system call. See man 2 gettid() for more details.

  This function can be called directly, but the more common usage pattern is through use of the
  identify_thread decorator on the run() function of a threading.Thread-derived object.

  Returns:
    on success, an int representing the thread ID of the calling thread, as returned from the
      gettid() system call. In the main thread of a process, this should be equal to the process
      ID (e.g. as returned by getpid())
    -1 on any failure (bad platform, error accessing ctypes/libraries, actual system call failure)

  """
  try:
    import platform
    if not platform.system().startswith('Linux'):
      raise ValueError
    syscalls = {
      'i386':   224,   # unistd_32.h: #define __NR_gettid 224
      'x86_64': 186,   # unistd_64.h: #define __NR_gettid 186
    }
    import ctypes
    tid = ctypes.CDLL('libc.so.6').syscall(syscalls[platform.machine()])
  except:
    tid = -1
  return tid


def identify_thread(instancemethod):
  """Simple decorator to expose Linux thread-IDs on an object.

  On Linux, each thread (aka light-weight process) is represented by a unique thread ID, an integer
  in the same namespace as process IDs. (This is distinct from the opaque identifier provided by the
  pthreads interface, which is essentially useless outside the context of the pthreads library.)
  This decorator provides a means to expose this thread ID on Python objects - most likely,
  subclasses of threading.Thread. The benefit of this is that operating-system level process
  information (for example, anything exposed through /proc on Linux) can then be correlated directly
  to Python thread objects.

  The means for retrieving the thread ID is extremely nonportable - specifically, it will only work
  on i386 and x86_64 Linux systems. However, including this decorator more generally should be safe
  and not break any cross-platform code - it will just result in an 'UNKNOWN' thread ID.

  This decorator can be used to wrap any instance method (and technically also class methods). To be
  truly useful, though, it should be used to wrap the run() function of a class utilising the Python
  threading API (i.e. a derivative of threading.Thread)

  Example usage:
    >>> import threading, time
    >>> from twitter.common.decorators import identify_thread
    >>> class MyThread(threading.Thread):
    ...   def __init__(self):
    ...     threading.Thread.__init__(self)
    ...     do_some_other_init()
    ...     self.daemon = True
    ...   @identify_thread
    ...   def run(self):
    ...     while True:
    ...       do_something_awesome()
    ...
    >>> thread1, thread2, thread3 = MyThread(), MyThread(), MyThread()
    >>> thread1.start(), thread2.start(), thread3.start()
    (None, None, None)
    >>> time.sleep(0.1)
    >>> for thread in (thread1, thread2, thread3):
    ...   print '%s => %s' % (thread.name, thread.__thread_id)
    ...
    Thread-1 => 19767
    Thread-2 => 19768
    Thread-3 => 19769
    >>> import os; os.system('ps -L -p %d -o pid,ppid,tid,thcount,cmd' % os.getpid())
      PID  PPID   TID THCNT CMD
    19764 19760 19764     4 /usr/bin/python2.6 /tmp/tmpSW3VIC
    19764 19760 19767     4 /usr/bin/python2.6 /tmp/tmpSW3VIC
    19764 19760 19768     4 /usr/bin/python2.6 /tmp/tmpSW3VIC
    19764 19760 19769     4 /usr/bin/python2.6 /tmp/tmpSW3VIC

  Note that there will be a non-zero delay between when the thread is started and when the thread ID
  attribute (self.__thread_id) is available.

  """
  @wraps(instancemethod)
  def identified(self, *args, **kwargs):
    tid = __gettid()
    if tid == -1:
      self.__thread_id = 'UNKNOWN'
    else:
      self.__thread_id = tid
      if isinstance(self, threading.Thread):
        self.setName('%s [TID=%d]' % (self.name, self.__thread_id))
    return instancemethod(self, *args, **kwargs)

  return identified
