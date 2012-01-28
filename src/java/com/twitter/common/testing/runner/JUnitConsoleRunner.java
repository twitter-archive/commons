package com.twitter.common.testing.runner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.apache.commons.lang.SystemUtils;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import com.twitter.common.application.AbstractApplication;
import com.twitter.common.application.AppLauncher;
import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgFilters;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.Positional;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.collections.Pair;

/**
 * An alternative to {@link JUnitCore} with stream capture and junit-report xml output capabilities.
 */
public class JUnitConsoleRunner extends AbstractApplication {

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

  @CmdLine(name = "suppress-output", help = "Suppresses test output.")
  private static final Arg<Boolean> SUPPRESS_OUTPUT = Arg.create(false);

  @CmdLine(name = "xmlreport", help = "Create ant compatible junit xml report files in -outdir.")
  private static final Arg<Boolean> ANT_JUNIT_XML = Arg.create(false);

  @CmdLine(name = "outdir",
           help = "Directory to output test captures too.  Only used if -suppress-output "
                  + "or -xmlreport is set.")
  private static final Arg<File> OUTDIR = Arg.create(SystemUtils.getJavaIoTmpDir());

  @NotEmpty
  @Positional(help = "Names of junit test classes or test methods to run.")
  private static final Arg<List<String>> TESTS = Arg.create();

  private static final Pattern METHOD_PARSER = Pattern.compile("^([^#]+)#([^#]+)$");

  @Override
  public void run() {
    PrintStream out = SWAPPABLE_OUT.getOriginal();
    List<Request> requests = parseRequests(out, TESTS.get());

    final JUnitCore core = new JUnitCore();
    ListenerRegistry listenerRegistry = new ListenerRegistry() {
      @Override public void addListener(RunListener listener) {
        core.addListener(listener);
      }
    };

    if (ANT_JUNIT_XML.get() || SUPPRESS_OUTPUT.get()) {
      File outdir = OUTDIR.get();
      if (!outdir.exists()) {
        if (!outdir.mkdirs()) {
          throw new IllegalStateException("Failed to create output directory: " + outdir);
        }
      }
      StreamCapturingListener streamCapturingListener = new StreamCapturingListener(outdir);
      listenerRegistry.addListener(streamCapturingListener);
      listenerRegistry = streamCapturingListener;

      if (ANT_JUNIT_XML.get()) {
        AntJunitXmlReportListener xmlReportListener =
            new AntJunitXmlReportListener(outdir, streamCapturingListener);
        listenerRegistry.addListener(xmlReportListener);
      }
    }

    listenerRegistry.addListener(new ConsoleListener(out));

    int failures = 0;
    for (Request request : requests) {
      Result result = core.run(request);
      failures += result.getFailureCount();
    }
    exit(failures);
  }

  private List<Request> parseRequests(PrintStream out, List<String> specs) {
    Set<Class<?>> classes = Sets.newLinkedHashSet();
    Set<Pair<Class<?>, String>> methods = Sets.newLinkedHashSet();
    for (String spec : specs) {
      Matcher matcher = METHOD_PARSER.matcher(spec);
      try {
        if (matcher.matches()) {
          Class<?> testClass = Class.forName(matcher.group(1));
          String method = matcher.group(2);
          methods.add(Pair.<Class<?>, String>of(testClass, method));
        } else {
          Class<?> testClass = Class.forName(spec);
          classes.add(testClass);
        }
      } catch (ClassNotFoundException e) {
        out.printf("WARNING: Skipping %s: %s\n", spec, e);
      }
    }
    List<Request> requests = Lists.newArrayList();
    if (!classes.isEmpty()) {
      requests.add(Request.classes(classes.toArray(new Class<?>[classes.size()])));
    }
    for (Pair<Class<?>, String> method : methods) {
      Class<?> testClass = method.getFirst();
      String testMethod = method.getSecond();
      requests.add(Request.method(testClass, testMethod));
    }
    return requests;
  }

  /**
   * Launcher for JUnitConsoleRunner.
   */
  public static void main(String[] args) {
    System.setOut(new PrintStream(SWAPPABLE_OUT));
    System.setErr(new PrintStream(SWAPPABLE_ERR));
    AppLauncher.launch(JUnitConsoleRunner.class, ArgFilters.selectClass(JUnitConsoleRunner.class),
        args);
  }

  private static void exit(int code) {
    // We're a main - its fine to exit.
    // SUPPRESS CHECKSTYLE RegexpSinglelineJava
    System.exit(code);
  }
}
