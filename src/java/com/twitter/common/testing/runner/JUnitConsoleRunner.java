package com.twitter.common.testing.runner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

/**
 * An alternative to {@link JUnitCore} with stream capture and junit-report xml output capabilities.
 */
public class JUnitConsoleRunner {

  private static final SwappableStream<PrintStream> SWAPPABLE_OUT =
      new SwappableStream<PrintStream>(System.out);

  private static final SwappableStream<PrintStream> SWAPPABLE_ERR =
      new SwappableStream<PrintStream>(System.err);

  /**
   * A stream that allows its underlying output to be swapped.
   */
  static class SwappableStream<T extends OutputStream> extends FilterOutputStream {
    private final T original;

    SwappableStream(T out) {
      super(out);
      this.original = out;
    }

    OutputStream swap(OutputStream out) {
      OutputStream old = this.out;
      this.out = out;
      return old;
    }

    /**
     * Returns the original stream this swappable stream was created with.
     */
    public T getOriginal() {
      return original;
    }
  }

  /**
   * Captures a tests stderr and stdout streams, restoring the previous streams on {@link #close()}.
   */
  static class StreamCapture {
    private final File out;
    private OutputStream outstream;

    private final File err;
    private OutputStream errstream;

    private int useCount;
    private boolean closed;

    StreamCapture(File out, File err) throws IOException {
      this.out = out;
      this.err = err;
    }

    void incrementUseCount() {
      this.useCount++;
    }

    void open() throws FileNotFoundException {
      if (outstream == null) {
        outstream = new FileOutputStream(out);
      }
      if (errstream == null) {
        errstream = new FileOutputStream(err);
      }
      SWAPPABLE_OUT.swap(outstream);
      SWAPPABLE_ERR.swap(errstream);
    }

    void close() throws IOException {
      if (--useCount <= 0 && !closed) {
        if (outstream != null) {
          Closeables.closeQuietly(outstream);
        }
        if (errstream != null) {
          Closeables.closeQuietly(errstream);
        }
        closed = true;
      }
    }

    void dispose() throws IOException {
      useCount = 0;
      close();
    }

    byte[] readOut() throws IOException {
      return read(out);
    }

    byte[] readErr() throws IOException {
      return read(err);
    }

    private byte[] read(File file) throws IOException {
      Preconditions.checkState(closed, "Capture must be closed by all users before it can be read");
      return Files.toByteArray(file);
    }
  }

  /**
   * A run listener that captures the output and error streams for each test class and makes the
   * content of these available.
   */
  static class StreamCapturingListener extends ForwardingListener implements StreamSource {
    private final Map<Class<?>, StreamCapture> captures = Maps.newHashMap();

    private final File outdir;

    StreamCapturingListener(File outdir) {
      this.outdir = outdir;
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
      registerTests(description.getChildren());
      super.testRunStarted(description);
    }

    private void registerTests(Iterable<Description> tests) throws IOException {
      for (Description test : tests) {
        registerTests(test.getChildren());
        if (Util.isRunnable(test)) {
          StreamCapture capture = captures.get(test.getTestClass());
          if (capture == null) {
            String prefix = test.getClassName();

            File out = new File(outdir, prefix + ".out.txt");
            Files.createParentDirs(out);

            File err = new File(outdir, prefix + ".err.txt");
            Files.createParentDirs(err);
            capture = new StreamCapture(out, err);
            captures.put(test.getTestClass(), capture);
          }
          capture.incrementUseCount();
        }
      }
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
      for (StreamCapture capture : captures.values()) {
        capture.dispose();
      }
      super.testRunFinished(result);
    }

    @Override
    public void testStarted(Description description) throws Exception {
      captures.get(description.getTestClass()).open();
      super.testStarted(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
      captures.get(description.getTestClass()).close();
      super.testFinished(description);
    }

    @Override
    public byte[] readOut(Class<?> testClass) throws IOException {
      return captures.get(testClass).readOut();
    }

    @Override
    public byte[] readErr(Class<?> testClass) throws IOException {
      return captures.get(testClass).readErr();
    }
  }

  private static final Pattern METHOD_PARSER = Pattern.compile("^([^#]+)#([^#]+)$");

  private final boolean failFast;
  private final boolean suppressOutput;
  private final boolean xmlReport;
  private final File outdir;

  JUnitConsoleRunner(boolean failFast, boolean suppressOutput, boolean xmlReport, File outdir) {
    this.failFast = failFast;
    this.suppressOutput = suppressOutput;
    this.xmlReport = xmlReport;
    this.outdir = outdir;
  }

  void run(Iterable<String> tests) {
    final PrintStream out = System.out;
    System.setOut(new PrintStream(SWAPPABLE_OUT));
    System.setErr(new PrintStream(SWAPPABLE_ERR));

    List<Request> requests = parseRequests(out, tests);

    JUnitCore core = new JUnitCore();
    final AbortableListener abortableListener = new AbortableListener(failFast) {
      @Override protected void abort(Result failureResult) {
        exit(failureResult.getFailureCount());
      }
    };
    core.addListener(abortableListener);

    if (xmlReport || suppressOutput) {
      if (!outdir.exists()) {
        if (!outdir.mkdirs()) {
          throw new IllegalStateException("Failed to create output directory: " + outdir);
        }
      }
      StreamCapturingListener streamCapturingListener = new StreamCapturingListener(outdir);
      abortableListener.addListener(streamCapturingListener);

      if (xmlReport) {
        AntJunitXmlReportListener xmlReportListener =
            new AntJunitXmlReportListener(outdir, streamCapturingListener);
        abortableListener.addListener(xmlReportListener);
      }
    }

    abortableListener.addListener(new ConsoleListener(out));

    Thread abnormalExitHook = new Thread() {
      @Override public void run() {
        try {
          abortableListener.abort(new UnknownError("Abnormal VM exit - test crashed."));
        } catch (Exception e) {
          out.println(e);
          e.printStackTrace(out);
        }
      }
    };
    abnormalExitHook.setDaemon(true);
    Runtime.getRuntime().addShutdownHook(abnormalExitHook);

    int failures = 0;
    for (Request request : requests) {
      Result result = core.run(request);
      failures += result.getFailureCount();
    }

    Runtime.getRuntime().removeShutdownHook(abnormalExitHook);
    exit(failures);
  }

  private List<Request> parseRequests(PrintStream out, Iterable<String> specs) {
    /**
     * Datatype representing an individual test method.
     */
    class TestMethod {
      private final Class<?> clazz;
      private final String name;
      TestMethod(Class<?> clazz, String name) {
        this.clazz = clazz;
        this.name = name;
      }
    }
    Set<TestMethod> testMethods = Sets.newLinkedHashSet();
    Set<Class<?>> classes = Sets.newLinkedHashSet();
    for (String spec : specs) {
      Matcher matcher = METHOD_PARSER.matcher(spec);
      try {
        if (matcher.matches()) {
          Class<?> testClass = Class.forName(matcher.group(1));
          if (isTest(testClass)) {
            String method = matcher.group(2);
            testMethods.add(new TestMethod(testClass, method));
          }
        } else {
          Class<?> testClass = Class.forName(spec);
          if (isTest(testClass)) {
            classes.add(testClass);
          }
        }
      } catch (NoClassDefFoundError e) {
        notFoundError(spec, out, e);
      } catch (ClassNotFoundException e) {
        notFoundError(spec, out, e);
      }
    }
    List<Request> requests = Lists.newArrayList();
    if (!classes.isEmpty()) {
      requests.add(Request.classes(classes.toArray(new Class<?>[classes.size()])));
    }
    for (TestMethod testMethod : testMethods) {
      requests.add(Request.method(testMethod.clazz, testMethod.name));
    }
    return requests;
  }

  private void notFoundError(String spec, PrintStream out, Throwable t) {
    out.printf("FATAL: Error during test discovery for %s: %s\n", spec, t);
    throw new RuntimeException("Classloading error during test discovery for " + spec, t);
  }

  /**
   * Launcher for JUnitConsoleRunner.
   */
  public static void main(String[] args) {
    /**
     * Command line option bean.
     */
    class Options {
      private boolean failFast = false;
      private boolean suppressOutput = false;
      private boolean xmlReport = false;
      private File outdir = new File(System.getProperty("java.io.tmpdir"));
      private List<String> tests = Lists.newArrayList();

      @Option(name = "-fail-fast", usage = "Causes the test suite run to fail fast.")
      public void setFailFast(boolean failFast) {
        this.failFast = failFast;
      }

      @Option(name = "-suppress-output", usage = "Suppresses test output.")
      public void setSuppressOutput(boolean suppressOutput) {
        this.suppressOutput = suppressOutput;
      }

      @Option(name = "-xmlreport",
              usage = "Create ant compatible junit xml report files in -outdir.")
      public void setXmlReport(boolean xmlReport) {
        this.xmlReport = xmlReport;
      }

      @Option(name = "-outdir",
              usage = "Directory to output test captures too.  Only used if -suppress-output or "
                      + "-xmlreport is set.")
      public void setOutdir(File outdir) {
        this.outdir = outdir;
      }

      @Argument(usage = "Names of junit test classes or test methods to run.  Names prefixed "
                        + "with @ are considered arg file paths and these will be loaded and the "
                        + "whitespace delimited arguments found inside added to the list",
                required = true,
                metaVar = "TESTS",
                handler = StringArrayOptionHandler.class)
      public void setTests(String[] tests) {
        this.tests = Arrays.asList(tests);
      }
    }

    Options options = new Options();
    CmdLineParser parser = new CmdLineParser(options);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      e.getParser().printUsage(System.out);
      exit(1);
    }

    JUnitConsoleRunner runner =
        new JUnitConsoleRunner(options.failFast,
            options.suppressOutput,
            options.xmlReport,
            options.outdir);

    List<String> tests = Lists.newArrayList();
    for (String test : options.tests) {
      if (test.startsWith("@")) {
        try {
          String argFileContents = Files.toString(new File(test.substring(1)), Charsets.UTF_8);
          tests.addAll(Arrays.asList(argFileContents.split("\\s+")));
        } catch (IOException e) {
          System.err.printf("Failed to load args from arg file %s: %s\n", test, e.getMessage());
          exit(1);
        }
      } else {
        tests.add(test);
      }
    }

    runner.run(tests);
  }

  private static boolean isTest(final Class<?> clazz) {
    // Must be a public concrete class to be a runnable junit Test.
    if (clazz.isInterface()
        || Modifier.isAbstract(clazz.getModifiers())
        || !Modifier.isPublic(clazz.getModifiers())) {
      return false;
    }

    // Support junit 3.x Test hierarchy.
    if (junit.framework.Test.class.isAssignableFrom(clazz)) {
      return true;
    }

    // Support junit 4.x @Test annotated methods.
    return Iterables.any(Arrays.asList(clazz.getMethods()), new Predicate<Method>() {
      @Override public boolean apply(Method method) {
        return Modifier.isPublic(method.getModifiers())
            && method.isAnnotationPresent(org.junit.Test.class);
      }
    });
  }

  private static void exit(int code) {
    // We're a main - its fine to exit.
    // SUPPRESS CHECKSTYLE RegexpSinglelineJava
    System.exit(code);
  }
}
