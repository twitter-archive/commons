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

package com.twitter.common.zookeeper;

import java.util.concurrent.TimeUnit;

/**
 * DistributedLock
 *
 * @author Florian Leibert
 */
public interface DistributedLock {
  void lock() throws LockingException;

  boolean tryLock(long timeout, TimeUnit unit);

  void unlock() throws LockingException;

  public static class LockingException extends RuntimeException {
    public LockingException(String msg, Exception e) {
      super(msg, e);
    }

    public LockingException(String msg) {
      super(msg);
    }
  }
}
