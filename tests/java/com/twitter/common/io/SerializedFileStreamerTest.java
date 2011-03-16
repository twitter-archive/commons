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

package com.twitter.common.io;

import com.google.common.collect.Lists;
import com.twitter.common.base.Closure;

import junit.framework.TestCase;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Gilad Mishne
 */
public class SerializedFileStreamerTest extends TestCase {
  private <T> void testStreamer(Streamer<T> streamer, List<T> objects) {
    final List<T> readObjects = Lists.newArrayList();
    streamer.process(new Closure<T>() {
      @Override public void execute(T t) {
        readObjects.add(t);
      }
    });
    assertEquals("Mismatch in number of objects", objects.size(), readObjects.size());
    for (int i = 0; i < objects.size(); ++i) {
      assertEquals("Mismatch in object #" + i, objects.get(i), readObjects.get(i));
    }
  }

  @Test
  public void test() throws Exception {
    File tmpDir = FileUtils.createTempDir();
    tmpDir.deleteOnExit();

    File uncompressed = new File(tmpDir, "data");
    File compressed = new File(tmpDir, "data.gz");
    GZIPOutputStream gzOutputStream = new GZIPOutputStream(new FileOutputStream(compressed));
    ObjectOutputStream compressedOS = new ObjectOutputStream(gzOutputStream);
    ObjectOutputStream uncompressedOS = new ObjectOutputStream(new FileOutputStream(uncompressed));

    List<String> strings = Lists.newArrayList("hello", "goodbye");
    for (String s : strings) {
      compressedOS.writeObject(s);
      uncompressedOS.writeObject(s);
    }
    gzOutputStream.finish();
    compressedOS.close();
    uncompressedOS.close();

    ObjectInputStream compressedIS =
        new ObjectInputStream(new GZIPInputStream(new FileInputStream(compressed)));
    ObjectInputStream uncompressedIS = new ObjectInputStream(new FileInputStream(uncompressed));

    testStreamer(new SerializedFileStreamer<String>(Lists.newArrayList(compressedIS)), strings);
    testStreamer(new SerializedFileStreamer<String>(Lists.newArrayList(uncompressedIS)), strings);
  }
}
