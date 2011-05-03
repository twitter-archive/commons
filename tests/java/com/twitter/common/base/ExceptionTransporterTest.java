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

package com.twitter.common.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John Sirois
 */
public class ExceptionTransporterTest {

  @Test(expected = FileNotFoundException.class)
  public void testCheckedTransport() throws FileNotFoundException {
    ExceptionTransporter.guard(new Function<ExceptionTransporter<FileNotFoundException>, File>() {
      @Override public File apply(ExceptionTransporter<FileNotFoundException> transporter) {
        throw transporter.transport(new FileNotFoundException());
      }
    });
  }

  @Test(expected = IllegalStateException.class)
  public void testUncheckedTransport() throws SocketException {
    ExceptionTransporter.guard(new Function<ExceptionTransporter<SocketException>, File>() {
      @Override public File apply(ExceptionTransporter<SocketException> transporter) {
        throw new IllegalStateException();
      }
    });
  }

  @Test
  public void testNoTransport() throws IOException {
    assertEquals("jake", ExceptionTransporter.guard(
        new Function<ExceptionTransporter<IOException>, String>() {
          @Override public String apply(ExceptionTransporter<IOException> exceptionTransporter) {
            return "jake";
          }
        }));
  }
}
