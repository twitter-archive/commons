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

package com.twitter.common.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * A simple dependency tracking compiler that maps generated classes to the owning sources.
 *
 * Supports a <pre>-dependencyfile</pre> option to output dependency information to in the form:
 * <pre>
 * [source file path] -&gt; [class file path]
 * </pre>
 *
 * There may be multiple lines per source file if the file contains multiple top level classes or
 * inner classes.  All paths are normalized to be relative to the classfile output directory.
 */
// SUPPRESS CHECKSTYLE HideUtilityClassConstructor
public final class Compiler {

  public static final String DEPENDENCYFILE_FLAG = "-dependencyfile";

  /**
   * A file manager that intercepts requests for class output files to track dependencies.
   */
  static final class DependencyTrackingFileManager
      extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final LinkedHashMap<String, List<String>> sourceToClasses =
        new LinkedHashMap<String, List<String>>();
    private final Set<String> priorSources = new HashSet<String>();
    private final File dependencyFile;

    private List<String> outputPath;
    private File outputDir;

    DependencyTrackingFileManager(StandardJavaFileManager fileManager, File dependencies)
        throws IOException {

      super(fileManager);
      this.dependencyFile = dependencies;

      if (dependencyFile.exists()) {
        System.out.println("Reading existing dependency file at " + dependencies);
        BufferedReader dependencyReader = new BufferedReader(new FileReader(dependencies));
        try {
          int line = 0;
          while (true) {
            String mapping = dependencyReader.readLine();
            if (mapping == null) {
              break;
            }

            line++;
            String[] components = mapping.split(" -> ");
            if (components.length != 2) {
              System.err.printf("Ignoring malformed dependency in %s[%d]: %s\n",
                  dependencies, line, mapping);
            } else {
              String sourceRelpath = components[0];
              String classRelpath = components[1];
              addMapping(sourceRelpath, classRelpath);
            }
          }
        } finally {
          dependencyReader.close();
        }
      }
      priorSources.addAll(sourceToClasses.keySet());
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
        FileObject sibling) throws IOException {

      JavaFileObject file = super.getJavaFileForOutput(location, className, kind, sibling);
      if (Kind.CLASS == kind) {
        addMapping(toOutputRelpath(sibling), toOutputRelpath(file));
      }
      return file;
    }

    private void addMapping(String sourceFile, String classFile) {
      List<String> classFiles = sourceToClasses.get(sourceFile);
      if (classFiles == null || priorSources.remove(sourceFile)) {
        classFiles = new ArrayList<String>();
        sourceToClasses.put(sourceFile, classFiles);
      }
      classFiles.add(classFile);
    }

    private String toOutputRelpath(FileObject file) {
      List<String> base = new ArrayList<String>(getOutputPath());
      List<String> path = toList(file);
      for (Iterator<String> baseIter = base.iterator(), pathIter = path.iterator();
           baseIter.hasNext() && pathIter.hasNext();) {
        if (!baseIter.next().equals(pathIter.next())) {
          break;
        } else {
          baseIter.remove();
          pathIter.remove();
        }
      }

      if (!base.isEmpty()) {
        path.addAll(0, Collections.nCopies(base.size(), ".."));
      }
      return join(path);
    }

    private String join(List<String> components) {
      StringBuilder path = new StringBuilder();
      for (int i = 0, max = components.size(); i < max; i++) {
        if (i > 0) {
          path.append(File.separatorChar);
        }
        path.append(components.get(i));
      }
      return path.toString();
    }

    private List<String> toList(FileObject path) {
      return new ArrayList<String>(Arrays.asList(path.toUri().normalize().getPath().split("/")));
    }

    private synchronized List<String> getOutputPath() {
      if (outputPath == null) {
        for (File path : fileManager.getLocation(StandardLocation.CLASS_OUTPUT)) {
          if (outputPath != null) {
            throw new IllegalStateException("Expected exactly 1 output path");
          }
          List<String> components = new ArrayList<String>();
          File f = path;
          while (f != null) {
            components.add(f.getName());
            f = f.getParentFile();
          }
          Collections.reverse(components);
          outputPath = components;
          outputDir = path;
        }
      }
      return outputPath;
    }

    @Override
    public void close() throws IOException {
      super.close();

      System.out.println("Writing class dependency file to " + dependencyFile);
      PrintWriter dependencyWriter = new PrintWriter(new FileWriter(dependencyFile, false));
      for (Entry<String, List<String>> entry : sourceToClasses.entrySet()) {
        String sourceFile = entry.getKey();
        for (String classFile : entry.getValue()) {
          if (!priorSources.contains(sourceFile)
              || (new File(outputDir, sourceFile).exists()
                  && new File(outputDir, classFile).exists())) {
            dependencyWriter.printf("%s -> %s\n", sourceFile, classFile);
          }
        }
      }
      dependencyWriter.close();
    }
  }

  /**
   * Should not be used; instead invoke {@link #main} directly.  Only present to conform to
   * a common compiler interface idiom expected by jmake.
   */
  public Compiler() {
  }

  /**
   * Passes through all args to the system java compiler and tracks classes generated for each
   * source file.
   *
   * @param args The command line arguments.
   * @return An exit code where 0 indicates successful compilation.
   * @throws IOException If there is a problem writing the dependency file.
   */
  public static int compile(String[] args) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager standardFileManager =
        compiler.getStandardFileManager(
            null, // use default diagnostic listener
            null, // default locale
            null); // default charset

    List<String> options = new ArrayList<String>();
    List<String> compilationUnits = new ArrayList<String>();
    File dependencyFile = null;
    for (Iterator<String> iter = Arrays.asList(args).iterator(); iter.hasNext();) {
      String arg = iter.next();
      if (DEPENDENCYFILE_FLAG.equals(arg)) {
        if (!iter.hasNext()) {
          System.err.printf("%s requires an argument specifying the output path\n",
              DEPENDENCYFILE_FLAG);
          return 1;
        }
        dependencyFile = new File(iter.next());
      } else if (arg.startsWith("-")) {
        int argCount = compiler.isSupportedOption(arg);
        if (argCount == -1) {
          argCount = standardFileManager.isSupportedOption(arg);
        }
        if (argCount == -1) {
          System.err.println("WARNING: Skipping unsupported option " + arg);
        } else {
          options.add(arg);
          while (argCount-- > 0) {
            if (iter.hasNext()) {
              options.add(iter.next());
            }
          }
        }
      } else {
        compilationUnits.add(arg);
      }
    }

    if (dependencyFile == null) {
      // Nothing special to do - just pass through and use the default output streams.
      return compiler.run(null, null, null, args);
    }

    DependencyTrackingFileManager fileManager =
        new DependencyTrackingFileManager(standardFileManager, dependencyFile);
    try {
      CompilationTask compilationTask =
          compiler.getTask(
              null, // use default output stream
              fileManager,
              null, // use default diagnostic listener
              options,
              null, // we specify no custom annotation processors manually here
              standardFileManager.getJavaFileObjectsFromStrings(compilationUnits));

      boolean success = compilationTask.call();
      return success ? 0 : 1;
    } finally {
      fileManager.close();
    }
  }

  /**
   * Passes through all args to the system java compiler and tracks classes generated for each
   * source file.
   *
   * @param args The command line arguments.
   * @throws IOException If there is a problem writing the dependency file.
   */
  public static void main(String[] args) throws IOException {
    exit(compile(args));
  }

  private static void exit(int code) {
    // We're a main - its fine to exit.
    // SUPPRESS CHECKSTYLE RegexpSinglelineJava
    System.exit(code);
  }
}
