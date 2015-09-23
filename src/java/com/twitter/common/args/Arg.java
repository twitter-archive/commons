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

package com.twitter.common.args;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Wrapper class for the value of an argument.  For proper behavior, an {@code Arg} should always
 * be annotated with {@link CmdLine}, which will define the command line interface to the argument.
 */
public class Arg<T> {

  @Nullable private final T defaultValue;
  @Nullable private T value;
  private boolean hasDefault = true;
  private boolean valueApplied = false;
  private boolean valueObserved = false;

  /**
   * Creates an arg that has no default value, meaning that its value can only ever be retrieved
   * if it has been externally set.
   */
  public Arg() {
    this(null);
    hasDefault = false;
  }

  /**
   * Creates an arg that has a default value, and may optionally be set.
   *
   * @param defaultValue The default value for the arg.
   */
  public Arg(@Nullable T defaultValue) {
    this.defaultValue = defaultValue;
    value = defaultValue;
  }

  synchronized void set(@Nullable T appliedValue) {
    Preconditions.checkState(!valueApplied, "A value cannot be applied twice to an argument.");
    Preconditions.checkState(!valueObserved, "A value cannot be changed after it was read.");
    valueApplied = true;
    value = appliedValue;
  }

  @VisibleForTesting
  synchronized void reset() {
    valueApplied = false;
    valueObserved = false;
    value = hasDefault ? defaultValue : null;
  }

  public boolean hasDefault() {
    return hasDefault;
  }

  /**
   * Gets the value of the argument.  If a value has not yet been applied to the argument, or the
   * argument did not provide a default value, {@link IllegalStateException} will be thrown.
   *
   * @return The argument value.
   */
  @Nullable
  public synchronized T get() {
    // TODO(William Farner): This has a tendency to break bad-arg reporting by ArgScanner.  Fix.
    Preconditions.checkState(valueApplied || hasDefault,
        "A value may only be retrieved from a variable that has a default or has been set.");
    valueObserved = true;
    return uncheckedGet();
  }

  /**
   * Checks whether a value has been applied to the argument (i.e., apart from the default).
   *
   * @return {@code true} if a value from the command line has been applied to this arg,
   *         {@code false} otherwise.
   */
  public synchronized boolean hasAppliedValue() {
    return valueApplied;
  }

  /**
   * Gets the value of the argument, without checking whether a default was available or if a
   * value was applied.
   *
   * @return The argument value.
   */
  @Nullable
  synchronized T uncheckedGet() {
    return value;
  }

  /**
   * Convenience factory method to create an arg that has no default value.
   *
   * @param <T> Type of arg value.
   * @return A new arg.
   */
  public static <T> Arg<T> create() {
    return new Arg<T>();
  }

  /**
   * Convenience factory method to create an arg with a default value.
   *
   * @param value Default argument value.
   * @param <T> Type of arg value.
   * @return A new arg.
   */
  public static <T> Arg<T> create(@Nullable T value) {
    return new Arg<T>(value);
  }
}
