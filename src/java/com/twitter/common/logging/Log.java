// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.logging;

import java.util.List;

/**
 * Logs messages to scribe.
 *
 * @author William Farner
 */
public interface Log<T, R> {

  /**
   * Submits a log message.
   *
   * @param entry Entry to log.
   * @return The result of the log request.
   */
  public R log(T entry);

  /**
   * Batch version of log.
   *
   * @param entries Entries to log.
   * @return The result of the log request.
   */
  public R log(List<T> entries);

  /**
   * Flushes the log, attempting to purge any state that is only stored locally.
   */
  public void flush();
}
