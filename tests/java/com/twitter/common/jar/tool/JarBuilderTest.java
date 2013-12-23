package com.twitter.common.jar.tool;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.InputSupplier;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.twitter.common.base.ExceptionalClosure;
import com.twitter.common.base.ExceptionalFunction;
import com.twitter.common.base.Function;
import com.twitter.common.jar.tool.JarBuilder.DuplicateAction;
import com.twitter.common.jar.tool.JarBuilder.DuplicateEntryException;
import com.twitter.common.jar.tool.JarBuilder.DuplicateHandler;
import com.twitter.common.jar.tool.JarBuilder.DuplicatePolicy;
import com.twitter.common.testing.easymock.EasyMockTest;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JarBuilderTest {

  public static class DuplicatePolicyTests {

    public static class DelegateTest extends EasyMockTest {
      @Test
      public void testPassDelegates() {
        Predicate<CharSequence> predicate = createMock(new Clazz<Predicate<CharSequence>>() { });
        expect(predicate.apply("foo")).andReturn(true);
        expect(predicate.apply("foo")).andReturn(false);
        control.replay();

        DuplicatePolicy policy = new DuplicatePolicy(predicate, DuplicateAction.REPLACE);

        assertEquals(DuplicateAction.REPLACE, policy.getAction());
        assertTrue(policy.apply("foo"));
        assertFalse(policy.apply("foo"));
      }
    }

    public static class MatchesTest {
      @Test
      public void test() {
        DuplicatePolicy anyA = DuplicatePolicy.pathMatches("a", DuplicateAction.CONCAT);
        DuplicatePolicy startA = DuplicatePolicy.pathMatches("^a", DuplicateAction.SKIP);
        DuplicatePolicy endA = DuplicatePolicy.pathMatches("a$", DuplicateAction.REPLACE);
        DuplicatePolicy exactlyA = DuplicatePolicy.pathMatches("^a$", DuplicateAction.THROW);

        assertEquals(DuplicateAction.CONCAT, anyA.getAction());
        assertEquals(DuplicateAction.SKIP, startA.getAction());
        assertEquals(DuplicateAction.REPLACE, endA.getAction());
        assertEquals(DuplicateAction.THROW, exactlyA.getAction());

        assertTrue(anyA.apply("bab"));
        assertTrue(anyA.apply("ab"));
        assertTrue(anyA.apply("ba"));
        assertFalse(anyA.apply("bbb"));

        assertTrue(startA.apply("ab"));
        assertTrue(startA.apply("aa"));
        assertFalse(startA.apply("bab"));
        assertFalse(startA.apply("ba"));

        assertTrue(endA.apply("ba"));
        assertTrue(endA.apply("aa"));
        assertFalse(endA.apply("bab"));
        assertFalse(endA.apply("ab"));

        assertTrue(exactlyA.apply("a"));
        assertFalse(exactlyA.apply("aa"));
        assertFalse(exactlyA.apply("b"));
      }
    }
  }

  public static class DuplicateHandlerTests {

    @RunWith(Parameterized.class)
    public static class AlwaysTest {

      @Parameters
      public static Collection<Object[]> data() {
        return FluentIterable.from(ImmutableList.copyOf(DuplicateAction.values()))
            .transform(new Function<DuplicateAction, Object[]>() {
              @Override
              public Object[] apply(DuplicateAction item) {
                return new Object[]{item};
              }
            }).toList();
      }

      private final DuplicateAction action;

      public AlwaysTest(DuplicateAction action) {
        this.action = action;
      }

      @Test
      public void test() {
        DuplicateHandler always = DuplicateHandler.always(action);
        assertEquals(action, always.actionFor("a"));
        assertEquals(action, always.actionFor("b"));
        assertEquals(action, always.actionFor("dalek"));
      }
    }

    public static class DuplicateHandlerTest extends EasyMockTest {
      @Test
      public void test() {
        Predicate<CharSequence> predicate1 = createMock(new Clazz<Predicate<CharSequence>>() { });
        Predicate<CharSequence> predicate2 = createMock(new Clazz<Predicate<CharSequence>>() { });

        // test1
        expect(predicate1.apply("a")).andReturn(true);

        // test2
        expect(predicate1.apply("a")).andReturn(false);
        expect(predicate2.apply("a")).andReturn(true);

        // test3
        expect(predicate1.apply("a")).andReturn(false);
        expect(predicate2.apply("a")).andReturn(false);

        control.replay();

        DuplicatePolicy policy1 = new DuplicatePolicy(predicate1, DuplicateAction.CONCAT);
        DuplicatePolicy policy2 = new DuplicatePolicy(predicate2, DuplicateAction.REPLACE);
        DuplicateHandler handler = new DuplicateHandler(DuplicateAction.THROW, policy1, policy2);

        assertEquals(DuplicateAction.CONCAT, handler.actionFor("a"));
        assertEquals(DuplicateAction.REPLACE, handler.actionFor("a"));
        assertEquals(DuplicateAction.THROW, handler.actionFor("a"));
      }
    }
  }

  public static class WriteTest {

    private static InputSupplier<? extends InputStream> content(String content) {
      return ByteStreams.newInputStreamSupplier(content.getBytes(Charsets.UTF_8));
    }

    private static String content(InputSupplier<? extends InputStream> content) throws IOException {
      return new String(ByteStreams.toByteArray(content), Charsets.UTF_8);
    }

    private static void assertListing(JarFile jar, String... paths) {
      assertListing(jar, Arrays.asList(paths), true);
    }

    private static void assertListingUnordered(JarFile jar, String... paths) {
      assertListing(jar, Arrays.asList(paths), false);
    }

    private static final ImmutableList<String> CONSTANT_ENTRIES =
        ImmutableList.of("META-INF/", "META-INF/MANIFEST.MF");

    private static void assertListing(JarFile jar, Iterable<String> paths, boolean ordered) {
      FluentIterable<String> expectedEntries =
          FluentIterable.from(Iterables.concat(CONSTANT_ENTRIES, paths));
      Collection<String> expected = ordered ? expectedEntries.toList() : expectedEntries.toSet();

      FluentIterable<String> actualEntries = FluentIterable.from(Collections.list(jar.entries()))
          .transform(new Function<JarEntry, String>() {
            @Override public String apply(JarEntry entry) {
              return entry.getName();
            }
          });
      Collection<String> actual = ordered ? actualEntries.toList() : actualEntries.toSet();

      assertEquals(expected, actual);
    }

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Closer tearDownCloser;

    @Before
    public void setUpCloser() {
      tearDownCloser = Closer.create();
    }

    @After
    public void tearDownCloser() throws IOException {
      tearDownCloser.close();
    }

    private void doWithJar(File path, final ExceptionalClosure<JarFile, IOException> work)
        throws IOException {

      doWithJar(path, new ExceptionalFunction<JarFile, Void, IOException>() {
        @Override public Void apply(JarFile jar) throws IOException {
          work.execute(jar);
          return null;
        }
      });
    }

    private <T> T doWithJar(File path, ExceptionalFunction<JarFile, T, IOException> work)
        throws IOException {

      Closer closer = Closer.create();
      JarFile jar = closer.register(new JarFile(path));
      try {
        return work.apply(jar);
      } catch (IOException e) {
        throw closer.rethrow(e);
      } finally {
        closer.close();
      }
    }

    private void assertContents(JarFile jar, String path, String expectedContent)
        throws IOException {

      Closer closer = Closer.create();
      InputStream entryIn = closer.register(jar.getInputStream(jar.getJarEntry(path)));
      try {
        assertEquals(expectedContent, new String(ByteStreams.toByteArray(entryIn), Charsets.UTF_8));
      } catch (IOException e) {
        throw closer.rethrow(e);
      } finally {
        closer.close();
      }
    }

    private JarBuilder jarBuilder() throws IOException {
      return jarBuilder(folder.newFile());
    }

    private JarBuilder jarBuilder(File destinationJar) {
      return tearDownCloser.register(new JarBuilder(destinationJar));
    }

    @Test
    public void testEmpty() throws IOException {
      File builtJar = jarBuilder().write();

      doWithJar(builtJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override
        public void execute(JarFile created) throws IOException {
          assertListing(created);
        }
      });
    }

      @Test
    public void testCustomManifestCreate() throws IOException {
      final Manifest customManifest = new Manifest();
      customManifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");

      File destinationJar = jarBuilder().useCustomManifest(customManifest).write();

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile created) throws IOException {
          assertListing(created);
          assertEquals(customManifest, created.getManifest());
        }
      });
    }

    @Test
    public void testCustomManifestUpdate() throws IOException {
      Manifest initial = new Manifest();
      initial.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
      initial.getMainAttributes().put(Name.MAIN_CLASS, "com.bob.Tool");

      File destinationJar = jarBuilder().useCustomManifest(initial).write();

      final Manifest manifest = doWithJar(destinationJar,
          new ExceptionalFunction<JarFile, Manifest, IOException>() {
            @Override public Manifest apply(JarFile created) throws IOException {
              assertListing(created);
              return created.getManifest();
            }
          });

      final Manifest customManifest = new Manifest();
      customManifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
      customManifest.getMainAttributes().put(Name.MAIN_CLASS, "com.john.Tool");

      jarBuilder(destinationJar).useCustomManifest(customManifest).write();

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile updated) throws IOException {
          assertListing(updated);
          Manifest updatedManifest = updated.getManifest();
          assertNotEqual(manifest, updatedManifest);
          assertEquals(customManifest, updatedManifest);
        }
      });
    }

    @Test
    public void testAddFile() throws IOException {
      File destinationJar = jarBuilder().add(content("42"), "meaning/of/life").write();

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar, "meaning/", "meaning/of/", "meaning/of/life");
          assertContents(jar, "meaning/of/life", "42");
        }
      });
    }

    @Test
    public void testAddDirectory() throws IOException {
      File dir = folder.newFolder("life/of/brian");
      FileUtils.write(new File(dir, "is"), "42");
      FileUtils.write(new File(dir, "used/to/be"), "1/137");

      File destinationJar = jarBuilder().add(dir, "meaning/of/life").write();

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListingUnordered(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life/",
              "meaning/of/life/is",
              "meaning/of/life/used/",
              "meaning/of/life/used/to/",
              "meaning/of/life/used/to/be");
          assertContents(jar, "meaning/of/life/is", "42");
          assertContents(jar, "meaning/of/life/used/to/be", "1/137");
        }
      });

    }

    @Test
    public void testAddJar() throws IOException {
      File sourceJar = jarBuilder().add(content("1/137"), "meaning/of/the/universe").write();

      File destinationJar =
          jarBuilder()
              .add(content("42"), "meaning/of/life")
              .addJar(sourceJar)
              .write();

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life",
              "meaning/of/the/",
              "meaning/of/the/universe");
          assertContents(jar, "meaning/of/life", "42");
          assertContents(jar, "meaning/of/the/universe", "1/137");
        }
      });
    }

    @Test
    public void testSkip() throws IOException {
      File dir = folder.newFolder("life/of/brian");
      FileUtils.write(new File(dir, "is"), "42");
      FileUtils.write(new File(dir, "used/to/be"), "4");

      File sourceJar =
          jarBuilder()
              .add(content("1/137"), "meaning/of/the/universe")
              .add(content("fine structure constant"), "meaning/of/the/universe/README")
              .write();

      File destinationJar =
          jarBuilder()
              .add(content("43"), "meaning/of/life/isn't")
              .add(dir, "meaning/of/life")
              .addJar(sourceJar)
              .write(
                  DuplicateHandler.always(DuplicateAction.THROW),
                  Pattern.compile("is$"),
                  Pattern.compile("/README"));

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life/",
              "meaning/of/life/isn't",
              "meaning/of/life/used/",
              "meaning/of/life/used/to/",
              "meaning/of/life/used/to/be",
              "meaning/of/the/",
              "meaning/of/the/universe");
          assertContents(jar, "meaning/of/life/isn't", "43");
          assertContents(jar, "meaning/of/life/used/to/be", "4");
          assertContents(jar, "meaning/of/the/universe", "1/137");
        }
      });
    }

    @Test
    public void testPolicyConcat() throws IOException {
      DuplicateHandler alwaysConcat = DuplicateHandler.always(DuplicateAction.CONCAT);

      File destinationJar = jarBuilder().add(content("1/137\n"), "meaning/of/life").write();

      jarBuilder(destinationJar)
          .add(content("42\n"), "meaning/of/life")
          .add(content("jake\n"), "meaning/of/life")
          .write(alwaysConcat);

      File jar = jarBuilder().add(content("more\n"), "meaning/of/life").write();

      File dir = folder.newFolder("life/of");
      FileUtils.write(new File(dir, "life"), "jane");

      jarBuilder(destinationJar)
          .addJar(jar)
          .add(dir, "meaning/of")
          .write(alwaysConcat);

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life");
          assertContents(jar, "meaning/of/life", "1/137\n42\njake\nmore\njane");
        }
      });
    }

    @Test
    public void testPolicySkip() throws IOException {
      DuplicateHandler alwaysSkip = DuplicateHandler.always(DuplicateAction.SKIP);

      File destinationJar =
          jarBuilder()
              .add(content("1/137"), "meaning/of/life")
              .add(content("!"), "meaning/of/life")
              .write(alwaysSkip);

      jarBuilder(destinationJar)
          .addJar(destinationJar)
          .add(content("42"), "meaning/of/life")
          .write(alwaysSkip);

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life");
          assertContents(jar, "meaning/of/life", "1/137");
        }
      });
    }

    @Test
    public void testPolicyReplace() throws IOException {
      DuplicateHandler replaceMeaningOfLife =
          new DuplicateHandler(DuplicateAction.SKIP,
              DuplicatePolicy.pathMatches("^meaning/of/life$", DuplicateAction.REPLACE));

      File destinationJar =
          jarBuilder()
              .add(content("1/137"), "meaning/of/life")
              .add(content("!"), "meaning/of/life")
              .add(content("1/137"), "meaning/of/brian")
              .write(replaceMeaningOfLife);

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life",
              "meaning/of/brian");
          assertContents(jar, "meaning/of/life", "!");
          assertContents(jar, "meaning/of/brian", "1/137");
        }
      });

      jarBuilder(destinationJar)
          .add(content("42"), "meaning/of/life")
          .add(content("jane"), "meaning/of/life")
          .write(replaceMeaningOfLife);

      doWithJar(destinationJar, new ExceptionalClosure<JarFile, IOException>() {
        @Override public void execute(JarFile jar) throws IOException {
          assertListing(jar,
              "meaning/",
              "meaning/of/",
              "meaning/of/life",
              "meaning/of/brian");
          assertContents(jar, "meaning/of/life", "jane");
          assertContents(jar, "meaning/of/brian", "1/137");
        }
      });
    }

    @Test
    public void testPolicyThrowPreExisting() throws IOException {
      DuplicateHandler alwaysThrow = DuplicateHandler.always(DuplicateAction.THROW);
      File destinationJar = jarBuilder().add(content("1/137"), "meaning/of/life").write();

      JarBuilder jarBuilder = jarBuilder(destinationJar).add(content("42"), "meaning/of/life");
      try {
        jarBuilder.write(alwaysThrow);
        fail("Expected jar processing to throw a DuplicateEntryException.");
      } catch (DuplicateEntryException e) {
        assertEquals("meaning/of/life", e.getPath());
        assertEquals("42", content(e.getSource()));
      }
    }

    @Test
    public void testPolicyThrowUser() throws IOException {
      DuplicateHandler alwaysThrow = DuplicateHandler.always(DuplicateAction.THROW);
      JarBuilder jarBuilder =
          jarBuilder()
              .add(content("1/137"), "meaning/of/life")
              .add(content("!"), "meaning/of/life");

      try {
        jarBuilder.write(alwaysThrow);
        fail("Expected jar processing to throw a DuplicateEntryException.");
      } catch (DuplicateEntryException e) {
        assertEquals("meaning/of/life", e.getPath());
        assertEquals("!", content(e.getSource()));
      }
    }
  }

  public static class RelpathTest {

    private static void assertRelpath(File fullPath, File relativeTo, String... entries) {
      assertEquals(ImmutableList.copyOf(entries),
          ImmutableList.copyOf(JarBuilder.relpathComponents(fullPath, relativeTo)));
    }

    @Test
    public void testRelpathExact() {
      assertRelpath(new File("/a/b/c"), new File("/a/b/c"));

      assertRelpath(new File("a/b/c"), new File("a/b/c"));
    }

    @Test
    public void testRelpathNested() {
      assertRelpath(new File("/a/b/c"), new File("/a"), "b", "c");
      assertRelpath(new File("/a/b/c"), new File("/a/b"), "c");

      assertRelpath(new File("a/b/c"), new File("a"), "b", "c");
      assertRelpath(new File("a/b/c"), new File("a/b"), "c");
    }

    @Test
    public void testRelpathSibling() {
      assertRelpath(new File("/a/b/c"), new File("/a/b/d"), "..", "c");
      assertRelpath(new File("/a/b/c"), new File("/a/d/e"), "..", "..", "b", "c");
      assertRelpath(new File("/a/b/c"), new File("/d/e/f"), "..", "..", "..", "a", "b", "c");

      assertRelpath(new File("a/b/c"), new File("a/b/d"), "..", "c");
      assertRelpath(new File("a/b/c"), new File("a/d/e"), "..", "..", "b", "c");
      assertRelpath(new File("a/b/c"), new File("d/e/f"), "..", "..", "..", "a", "b", "c");
    }
  }
}
