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

package com.twitter.common.stats;

import junit.framework.TestCase;

public class PrintableHistogramTest extends TestCase {

  public void testPrintHistogram() {
    PrintableHistogram hist = new PrintableHistogram(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
    for (int i = 10; i > 0; i--) {
      hist.addValue(i * 10, 10 - i);
    }
    System.out.println(hist);
  }
}
