// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

package com.twitter.common.runtime;

import java.io.File;

import com.google.common.io.Files;

import org.junit.Test;

import com.twitter.common.base.ExceptionalClosure;
import com.twitter.common.io.FileUtils;
import com.twitter.common.runtime.NativeLoader.NativeResource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeLoaderTest {
  public abstract static class NativeResourceTest {
    @Test
    public final void test() throws Exception {
      FileUtils.SYSTEM_TMP.doWithDir(new ExceptionalClosure<File, Exception>() {
        @Override public void execute(File libDir) throws Exception {
          doTest(libDir, NativeResource.parse(libDir, true, getLine()));
        }
      });
    }

    protected abstract String getLine();
    protected abstract void doTest(File libDir, NativeResource nativeResource) throws Exception;
  }

  public static class TestBasics extends NativeResourceTest {
    @Override protected String getLine() {
      return "com/twitter/common/runtime/foo.fakelib";
    }
    @Override protected void doTest(File libDir, NativeResource nativeResource) throws Exception {
      assertFalse(nativeResource.isLoadable());
      assertEquals(new File(libDir, getLine()), nativeResource.getFile());
    }
  }

  public static class TestLoadable extends NativeResourceTest {
    @Override protected String getLine() {
      return "*com/twitter/common/runtime/foo.fakelib";
    }
    @Override protected void doTest(File libDir, NativeResource nativeResource) throws Exception {
      assertTrue(nativeResource.isLoadable());
    }
  }

  public static class TestLinking extends NativeResourceTest {
    @Override protected String getLine() {
      return "com/twitter/common/runtime/foo.fakelib bar.fakelib baz.fakelib";
    }
    @Override protected void doTest(File libDir, NativeResource nativeResource) throws Exception {
      assertFalse(nativeResource.isLoadable());
      nativeResource.extract();

      File bar = new File(libDir, "bar.fakelib");
      assertTrue(bar.exists());

      File baz = new File(libDir, "baz.fakelib");
      assertTrue(baz.exists());

      byte[] barContents = Files.toByteArray(bar);
      assertArrayEquals(Files.toByteArray(nativeResource.getFile()), barContents);
      assertArrayEquals(barContents, Files.toByteArray(baz));
    }
  }
}
