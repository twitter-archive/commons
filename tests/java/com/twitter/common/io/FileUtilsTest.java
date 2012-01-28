package com.twitter.common.io;

import java.io.File;
import java.io.IOException;

import com.google.common.io.Files;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.JUnitAsserts;
import com.google.common.testing.junit4.TearDownTestCase;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.ExceptionalClosure;
import com.twitter.common.base.ExceptionalFunction;
import com.twitter.common.base.Function;
import com.twitter.common.io.FileUtils.Temporary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class FileUtilsTest extends TearDownTestCase {

  private Temporary temporary;

  @Before
  public void setUp() {
    final File tmpDir = FileUtils.createTempDir();
    addTearDown(new TearDown() {
      @Override public void tearDown() throws Exception {
        org.apache.commons.io.FileUtils.deleteDirectory(tmpDir);
      }
    });
    assertEmptyDir(tmpDir);

    temporary = new Temporary(tmpDir);
  }

  @Test
  public void testCreateDir() {
    File tmpDir = temporary.createDir();
    assertEmptyDir(tmpDir);
  }

  @Test
  public void testCreateFile() throws IOException {
    File tmpFile = temporary.createFile(".jake");
    assertEmptyFile(tmpFile);
    JUnitAsserts.assertMatchesRegex(".+\\.jake$", tmpFile.getName());
  }

  @Test
  public void testDoWithDir() {
    assertEquals("42", temporary.doWithDir(new Function<File, String>() {
      @Override public String apply(File dir) {
        assertEmptyDir(dir);
        return "42";
      }
    }));
  }

  static class MarkerException extends Exception {}

  @Test(expected = MarkerException.class)
  public void testDoWithDir_bubbles() throws MarkerException {
    temporary.doWithDir(new ExceptionalClosure<File, MarkerException>() {
      @Override public void execute (File dir) throws MarkerException {
        throw new MarkerException();
      }
    });
  }

  @Test
  public void testDoWithFile() throws IOException {
    assertEquals("37", temporary.doWithFile(new ExceptionalFunction<File, String, IOException>() {
      @Override public String apply(File file) throws IOException {
        assertEmptyFile(file);
        return "37";
      }
    }));
  }

  @Test(expected = MarkerException.class)
  public void testDoWithFile_bubbles() throws MarkerException, IOException {
    temporary.doWithFile(new ExceptionalClosure<File, MarkerException>() {
      @Override public void execute(File dir) throws MarkerException {
        throw new MarkerException();
      }
    });
  }

  private void assertEmptyDir(File dir) {
    assertNotNull(dir);
    assertTrue(dir.exists());
    assertTrue(dir.isDirectory());
    assertEquals(0, dir.list().length);
  }

  private void assertEmptyFile(File file) throws IOException {
    assertNotNull(file);
    assertTrue(file.exists());
    assertTrue(file.isFile());
    assertEquals(0, Files.toByteArray(file).length);
  }
}
