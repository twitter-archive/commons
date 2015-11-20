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

/**
 * In a Module(or other class), many Args are declared like so
 *
 * private static final Arg<Integer> NUM_PARTITIONS = Arg.create();
 *
 * When a test uses the production code to create the modules.  The modules then have
 * default constructors, there is no good way to pass info to the args and the tests
 * ends up calling Arg.set on the same fields which fails during tests since we want
 * to recreate the server multiple times.
 *
 * This class fixes that so we can put Arg.java into a TestMode and don't have to add
 * @VisibleForTesting annotation to our code at all.  We then get to remove test code from
 * our server (ideally src/java/main never has test code, and this lets many servers not to
 * have that test code in their production code).
 */
public final class TestMode {

  private TestMode() {
  }

  private static boolean isTestMode = false;

  public static boolean isTestMode() {
    return isTestMode;
  }

  public static void setTestMode(boolean testMode) {
    isTestMode = testMode;
  }
}
