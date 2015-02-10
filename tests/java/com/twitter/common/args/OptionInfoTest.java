// =================================================================================================
// Copyright 2015 Twitter, Inc.
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

import java.io.File;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.TemporaryFolder;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class OptionInfoTest {
  private static class App {
    @CmdLine(name = "files", help = "help.", argFile = true)
    private final Arg<List<File>> files = Arg.<List<File>>create(ImmutableList.<File>of());

    @CmdLine(name = "flag", help = "help.")
    private final Arg<Boolean> flag = Arg.create();
  }

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();
  private App app;

  @Before
  public void setUp() throws Exception {
    app = new App();
  }

  @Test
  public void testArgumentFilesCreateFromField() throws Exception {
    OptionInfo optionInfo = OptionInfo.createFromField(App.class.getDeclaredField("files"), app);
    assertEquals("files", optionInfo.getName());
    assertEquals(
        String.format(OptionInfo.ARG_FILE_HELP_TEMPLATE, "help.", "files", "files"),
        optionInfo.getHelp());
    assertTrue(optionInfo.argFile());
    assertEquals("com.twitter.common.args.OptionInfoTest.App.files",
        optionInfo.getCanonicalName());
  }

  @Test
  public void testArgumentFilesRegularFormat() throws Exception {
    new ArgScanner().parse(Args.from(ArgFilters.selectClass(App.class), app),
        ImmutableList.of("-files=1.txt,2.txt"));
    assertEquals(
        ImmutableList.of(new File("1.txt"), new File("2.txt")),
        app.files.get());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testArgumentFilesArgFileFormatEmptyFileName() throws Exception {
    new ArgScanner().parse(Args.from(ArgFilters.selectClass(App.class), app),
        ImmutableList.of("-files=@"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testArgumentFilesArgFileFormatFileNotExist() throws Exception {
    new ArgScanner().parse(Args.from(ArgFilters.selectClass(App.class), app),
        ImmutableList.of("-files=@file_does_not_exist.txt"));
  }

  @Test
  public void testArgumentFilesArgFileFormat() throws Exception {
    File argfile = tmpDir.newFile();
    // Note the '\n' at the end. Some editors auto add a newline at the end so
    // make sure our arg scanner and parser can deal with this.
    Files.write("1.txt,2.txt\n", argfile, Charsets.UTF_8);
    new ArgScanner().parse(Args.from(ArgFilters.selectClass(App.class), app),
        ImmutableList.of("-files=@" + argfile.getCanonicalPath()));
    assertEquals(
        ImmutableList.of(new File("1.txt"), new File("2.txt")),
        app.files.get());
  }

  @Test
  public void testArgumentFlagCreateFromField() throws Exception {
    OptionInfo optionInfo = OptionInfo.createFromField(App.class.getDeclaredField("flag"), app);
    assertEquals("flag", optionInfo.getName());
    assertEquals("help.", optionInfo.getHelp());
    assertFalse(optionInfo.argFile());
    assertEquals("com.twitter.common.args.OptionInfoTest.App.flag", optionInfo.getCanonicalName());
    assertEquals("no_flag", optionInfo.getNegatedName());
    assertEquals(
        "com.twitter.common.args.OptionInfoTest.App.no_flag", optionInfo.getCanonicalNegatedName());
  }
}
