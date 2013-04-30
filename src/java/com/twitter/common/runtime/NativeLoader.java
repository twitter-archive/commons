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
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import com.twitter.common.base.Function;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.io.FileUtils;

/**
 * Provides a facility for extracting and optionally loading native libraries from classpath
 * resources.
 *
 * <p>NativeLoader can be used in 2 modes, possibly intermixed:
 * <ol>
 *   <li>To establish a path to adjoin to the native library path on the system at hand.</li>
 *   <li>To load contained jni libraries.</li>
 * </ol>
 *
 * <p>In the 1st mode the transitive set of non-core libraries needed by a java jni application
 * would be included as native resources inside jars, extracted to a directory and adjoined to the
 * library path.  On linux this would might look like:
 * <pre>
 *   #!/bin/bash
 *
 *   MY_NATIVE_LIBS=$(mktemp -d)
 *   trap "rm -r $MY_NATIVE_LIBS" EXIT
 *   LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$MY_NATIVE_LIBS \
 *     java -Dnativeloader.library.path=$MY_NATIVE_LIBS \
 *          -cp ...
 * </pre>
 * </p>
 *
 * <p>In the second mode, rather than using System.loadLibrary([libname]) and adjusting
 * java.library.path so that the jvm can find your jni libs, you instead let NativeLoader load your
 * jni libraries from the extracted resources directly.  This loading is triggered by special syntax
 * described below.
 * </p>
 *
 * <p>NativeLoader relies on a special classpath manifest resource to describe native libraries in
 * the classpath.  The resource path is {@code META-INF/native.mf} and the contents is a
 * line-oriented listing of classpath native library resources.  Each library line can begin with a
 * single optional {@code '*'} indicating the library should be {@link System#load(String) loaded}.
 * This is followed by the classpath resource name of the native library and optionally followed by
 * one or more whitespace separated paths to link the resource to when it is extracted onto the
 * filesystem.
 *
 * <p>For example:
 * <pre>
 *   # We extract the manifest itself too.  We don't need to have this entry but any resource can be
 *   # extracted even if its not actually a native library.
 *   META-INF/native.mf
 *
 *   # We extract libopencv_core.2.4.2.dylib from the classpath but also create a symlink for
 *   # libopencv_core.2.4.dylib in the extraction directory.
 *   libopencv_core.2.4.2.dylib    libopencv_core.2.4.dylib
 *
 *   # The next 2 libraries are loaded and java.library.path need not be modified.
 *   *libjnimesos.dylib
 *   *mecab.dylib
 * </pre>
 * </p>
 *
 * <p>Extracts to:
 * <pre>
 *   [lib dir]/META-INF/native.mf
 *   [lib dir]/libopencv_core.2.4.2.dylib
 *   [lib dir]/libopencv_core.2.4.dylib -> libopencv_core.2.4.2.dylib
 *   [lib dir]/libjnimesos.dylib
 *   [lib dir]/mecab.dylib
 * </pre>
 * </p>
 *
 * Note that although order does not matter for parsing it is respected when loading libraries
 * marked with the load ({@code '*'}) directive.  In the example above 1st all extraction is done
 * and then libjnimesos.dylib is loaded followed by mecab.dylib.
 */
public final class NativeLoader {
  private static final Logger LOG = Logger.getLogger(NativeLoader.class.getName());

  private static class Global {
    private static File getLibPath() {
      String libPath =
          System.getProperty("nativeloader.library.path",
              System.getenv("NATIVELOADER_LIBRARY_PATH"));
      if (libPath != null) {
        return new File(libPath);
      } else {
        return FileUtils.createTempDir();
      }
    }

    private static boolean getDeleteExtractedOnExit() {
      return Boolean.getBoolean("nativeloader.deleteonexit");
    }

    static final NativeLoader LOADER = new NativeLoader(getLibPath(), getDeleteExtractedOnExit());
  }

  /**
   * Extracts any registered native libraries from the classpath and loads those marked for load.
   *
   * <p>Extraction behavior can be controlled through a combination of environment variables and
   * system properties:
   * <ul>
   *   <li>NATIVELOADER_LIBRARY_PATH: An environment variable containing the path to extract to.
   *   If not present a new random temp dir will be used.</li>
   *   <li>nativeloader.library.path: A system property containing the path to extract to.  If not
   *   present then NATIVELOADER_LIBRARY_PATH is used.</li>
   *   <li>nativeloader.deleteonexit: A system property that can be set to 'false' to turn off the
   *   default delete-on-exit of extracted resources.</li>
   * </ul>
   * </p>
   *
   * <p>A common use for this static method would be as a replacement for a typical:
   * <pre>
   *   class MyNativeBridge {
   *     static {
   *       System.loadLibrary('mesos');
   *     }
   *   }
   * </pre>
   *
   * With:
   * <pre>
   *   class MyNativeBridge {
   *     static {
   *       NativeLoader.loadLibs();
   *     }
   *   }
   * </pre>
   * </p>
   * @return The native resources found on the classpath and extracted.
   */
  public static ImmutableList<NativeResource> loadLibs() {
    return Global.LOADER.load();
  }

  /**
   * Indicates an error loading a native library.
   */
  public static class NativeLoadError extends Error {
    public NativeLoadError(Throwable cause) {
      super(cause);
    }
  }

  private final File libPath;
  private final boolean deleteExtractedOnExit;

  /**
   * Creates a native loader that extracts any registered native libraries to the designated
   * {@code libPath}.
   *
   * @param libPath The path to extract native library resources to.
   * @param deleteExtractedOnExit If {@code true}, extracted libraries will be deleted when the jvm
   *     exits.
   */
  public NativeLoader(File libPath, boolean deleteExtractedOnExit) {
    Preconditions.checkNotNull(libPath);
    Preconditions.checkArgument(
        libPath.exists() || libPath.mkdirs(),
        "%s does not exist and failed to create it",
        libPath);
    Preconditions.checkArgument(libPath.canWrite(), "%s exists but cannot write to it", libPath);

    this.libPath = libPath;
    this.deleteExtractedOnExit = deleteExtractedOnExit;
  }

  private final Supplier<ImmutableList<NativeResource>> nativeResources =
      Suppliers.memoize(new Supplier<ImmutableList<NativeResource>>() {
        @Override public ImmutableList<NativeResource> get() {
          try {
            return extractLibs();
          } catch (IOException e) {
            throw new NativeLoadError(e);
          }
        }
      });

  /**
   * Extracts any registered native libraries from the classpath, creates links (or copies) as
   * needed and loads those libraries marked for load.  This method is idempotent and will only do
   * extraction and loading on the first call.  All subsequent calls will just return the list
   * of already loaded native resources.
   *
   * @return The native resources that were loaded.
   */
  public ImmutableList<NativeResource> load() {
    return nativeResources.get();
  }

  ImmutableList<NativeResource> extractLibs() throws IOException {
    // Extract all native libs before loading them - libs may interdepend and need sibling loose
    // on the path to resolve fully.

    ImmutableList.Builder<NativeResource> resourceBuilder = ImmutableList.builder();
    for (NativeResource nativeResource : findNativeResources()) {
      nativeResource.extract();
      resourceBuilder.add(nativeResource);
    }
    ImmutableList<NativeResource> resources = resourceBuilder.build();

    for (NativeResource nativeResource : resources) {
      nativeResource.maybeLoad();
    }

    return resources;
  }

  private Iterable<NativeResource> findNativeResources() throws IOException {
    Enumeration<URL> resourcesEnumeration =
        getClass().getClassLoader().getResources("META-INF/native.mf");

    Set<NativeResource> resources = Sets.newLinkedHashSet();
    while (resourcesEnumeration.hasMoreElements()) {
      URL manifestUrl = resourcesEnumeration.nextElement();
      for (String line : Resources.readLines(manifestUrl, Charsets.UTF_8)) {
        String normalizedLine = line.trim();
        if (!normalizedLine.startsWith("#")) {
          NativeResource nativeResource =
              NativeResource.parse(libPath, deleteExtractedOnExit, normalizedLine);
          if (!resources.add(nativeResource)) {
            throw new IllegalStateException(
                "Already detected a native resource for " + normalizedLine + " in " + manifestUrl);
          }
        }
      }
    }
    LOG.info("Found native resources: " + resources);
    return resources;
  }

  /**
   * Describes a native resource that extracts to a library path.
   */
  public static final class NativeResource {

    static NativeResource parse(File libPath, boolean deleteOnExit, String normalizedLine) {
      if (normalizedLine.startsWith("*")) {
        return new NativeResource(libPath, normalizedLine.substring(1), true, deleteOnExit);
      } else {
        return new NativeResource(libPath, normalizedLine, false, deleteOnExit);
      }
    }

    private final File file;
    private Set<File> links;
    private final String name;
    private final boolean loadable;
    private final boolean deleteOnExit;

    private NativeResource(
        final File basedir,
        String names,
        boolean loadable,
        boolean deleteOnExit) {

      Iterable<String> nameAndLinks =
          Lists.newArrayList(Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().split(names));
      MorePreconditions.checkNotBlank(nameAndLinks);

      Iterator<String> paths = Iterators.consumingIterator(nameAndLinks.iterator());

      name = paths.next();

      Function<String, File> createPath = new Function<String, File>() {
        @Override public File apply(String item) {
          return new File(basedir, item);
        }
      };
      file = createPath.apply(name);
      links = ImmutableSet.copyOf(Iterators.transform(paths, createPath));

      this.loadable = loadable;
      this.deleteOnExit = deleteOnExit;
    }

    void extract() throws IOException {
      if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
        throw new IOException("Failed to create parent dir for " + this);
      }

      LOG.info("Extracting " + this);
      Files.copy(Resources.newInputStreamSupplier(Resources.getResource(name)), file);
      if (deleteOnExit) {
        file.deleteOnExit();
      }

      // TODO(John Sirois): really just link - java 7 supports this.
      for (File link : links) {
        LOG.info(String.format("Linking %s -> %s", link, file));
        Files.copy(file, link);
        if (deleteOnExit) {
          link.deleteOnExit();
        }
      }
    }

    void maybeLoad() {
      if (loadable) {
        LOG.info("Loading " + this);
        System.load(file.getPath());
      }
    }

    /**
     * Returns the file the native library is extracted to.
     */
    public File getFile() {
      return file;
    }

    /**
     * Returns the resource name of the classpath embedded native resource.
     */
    public String getName() {
      return name;
    }

    /**
     * Returns {@code true} if the native resource is loadable via {@link System#load(String)}.
     */
    public boolean isLoadable() {
      return loadable;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .add("name", name)
          .add("file", file)
          .add("loadable", loadable)
          .add("links", links)
          .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof NativeResource)) {
        return false;
      }

      NativeResource that = (NativeResource) o;
      return Objects.equal(file, that.file)
          &&  Objects.equal(name, that.name)
          &&  Objects.equal(loadable, that.loadable);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(file, name, loadable);
    }
  }
}
