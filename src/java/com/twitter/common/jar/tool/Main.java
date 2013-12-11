package com.twitter.common.jar.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgParser;
import com.twitter.common.args.ArgScanner;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.Positional;
import com.twitter.common.args.constraints.CanRead;
import com.twitter.common.args.constraints.Exists;
import com.twitter.common.jar.tool.JarBuilder.DuplicateAction;
import com.twitter.common.jar.tool.JarBuilder.DuplicateEntryException;
import com.twitter.common.jar.tool.JarBuilder.DuplicateHandler;
import com.twitter.common.jar.tool.JarBuilder.DuplicatePolicy;
import com.twitter.common.jar.tool.JarBuilder.Entry;
import com.twitter.common.jar.tool.JarBuilder.Listener;
import com.twitter.common.jar.tool.JarBuilder.Source;
import com.twitter.common.logging.RootLogConfig;
import com.twitter.common.logging.RootLogConfig.Configuration;
import com.twitter.common.logging.RootLogConfig.LogLevel;

public final class Main {
  @ArgParser
  static class DuplicatePolicyParser implements Parser<DuplicatePolicy> {
    private static final Splitter REGEX_ACTION_SPLITTER =
        Splitter.on("=").trimResults().omitEmptyStrings();

    @Override
    public DuplicatePolicy parse(ParserOracle parserOracle, Type type, String raw)
        throws IllegalArgumentException {

      List<String> components = ImmutableList.copyOf(REGEX_ACTION_SPLITTER.split(raw));
      Preconditions.checkArgument(components.size() == 2,
          "Failed to parse jar path regex/action pair " + raw);

      String regex = components.get(0);

      Parser<DuplicateAction> actionParser = parserOracle.get(TypeToken.of(DuplicateAction.class));
      DuplicateAction action =
          actionParser.parse(parserOracle, DuplicateAction.class, components.get(1));

      return DuplicatePolicy.pathMatches(regex, action);
    }
  }

  @CmdLine(name = "main",
      help = "The name of the fully qualified main class."
          + " Its an error to specify both a main and a manifest.")
  private static final Arg<String> MAIN_CLASS = Arg.create(null);

  @Exists
  @CanRead
  @CmdLine(name = "manifest",
      help = "A path to a manifest file to use. "
          + "Its an error to specify both a main and a manifest.")
  private static final Arg<File> MANIFEST = Arg.create(null);

  @CmdLine(name = "update", help = "Update the jar if it already exists, otherwise create it.")
  private static final Arg<Boolean> UPDATE = Arg.create(false);

  @CmdLine(name = "files",
      help = "A mapping from files to add to the path to add them at in the jar")
  private static final Arg<Map<File, String>> FILES =
      Arg.<Map<File, String>>create(ImmutableMap.<File, String>of());

  @CmdLine(name = "jars", help = "A list of jar files whose entries to add to the output jar")
  private static final Arg<List<File>> JARS = Arg.<List<File>>create(ImmutableList.<File>of());

  @CmdLine(name = "default_action",
      help = "The default duplicate action to apply if no policies match.")
  private static final Arg<DuplicateAction> DEFAULT_ACTION = Arg.create(DuplicateAction.SKIP);

  @CmdLine(name = "policies", help = "A list of duplicate policies to apply.")
  private static final Arg<List<DuplicatePolicy>> POLICIES =
      Arg.<List<DuplicatePolicy>>create(ImmutableList.<DuplicatePolicy>of());

  @Positional(help = "The target jar file path to write.")
  private static final Arg<List<File>> TARGET = Arg.create();

  private static final Logger LOG = Logger.getLogger(Main.class.getName());

  private static class LoggingListener implements Listener {
    private Source source = null;
    private final File target;

    LoggingListener(File target) {
      this.target = target;
    }

    @Override
    public void onSkip(Entry original, Iterable<? extends Entry> skipped) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine(String.format("Retaining %s and skipping %s", identify(original),
            identify(skipped)));
      }
    }

    @Override
    public void onReplace(Iterable<? extends Entry> originals, Entry replacement) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine(String.format("Using %s to replace %s", identify(replacement),
            identify(originals)));
      }
    }

    @Override
    public void onConcat(String entryName, Iterable<? extends Entry> entries) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine(String.format("Concatenating %s!%s from %s", target.getPath(), entryName,
            identify(entries)));
      }
    }

    @Override
    public void onWrite(Entry entry) {
      if (!entry.getSource().equals(source)) {
        source = entry.getSource();
        LOG.fine(entry.getSource().name());
      }
      LOG.log(Level.FINER, "\t{0}", entry.getName());
    }

    private static String identify(Entry entry) {
      return entry.getSource().identify(entry.getName());
    }

    private static String identify(Iterable<? extends Entry> entries) {
      return Joiner.on(",").join(
          FluentIterable.from(entries).transform(new Function<Entry, String>() {
            @Override public String apply(Entry input) {
              return identify(input);
            }
          }));
    }
  }

  private Main() {
    // tool
  }

  /**
   * Creates or updates a jar with specified files, directories and jar files.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    Configuration bootstrapLogConfig =
        RootLogConfig.builder()
            .logToStderr(true)
            .useGLogFormatter(true)
            .vlog(LogLevel.WARNING)
            .build();
    bootstrapLogConfig.apply();

    ArgScanner argScanner = new ArgScanner();
    if (!argScanner.parse(Arrays.asList(args))) {
      exit(1);
    }

    Configuration logConfig = RootLogConfig.configurationFromFlags();
    logConfig.apply();

    if (MAIN_CLASS.hasAppliedValue() && MANIFEST.hasAppliedValue()) {
      exit(1, "Can specify main or manifest but not both.");
    }
    if (TARGET.get().size() != 1) {
      exit(1, "Must supply exactly 1 target jar path.");
    }
    final File target = Iterables.getOnlyElement(TARGET.get());

    if (!UPDATE.get() && target.exists() && !target.delete()) {
      exit(1, "Failed to delete file at requested target path %s", target);
    }

    final Closer closer = Closer.create();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override public void run() {
        try {
          closer.close();
        } catch (IOException e) {
          LOG.warning("Failed to close one or more resources: " + e);
        }
      }
    });
    JarBuilder jarBuilder = closer.register(new JarBuilder(target, new LoggingListener(target)));

    if (MAIN_CLASS.hasAppliedValue()) {
      Manifest manifest = JarBuilder.createDefaultManifest();
      manifest.getMainAttributes().put(Name.MAIN_CLASS, MAIN_CLASS.get());
      jarBuilder.useCustomManifest(manifest);
    } else if (MANIFEST.hasAppliedValue()) {
      jarBuilder.useCustomManifest(MANIFEST.get());
    }

    for (Map.Entry<File, String> entry : FILES.get().entrySet()) {
      File file = entry.getKey();
      String jarPath = entry.getValue();
      jarBuilder.add(file, jarPath);
    }

    for (File jar : JARS.get()) {
      jarBuilder.addJar(jar);
    }

    DuplicateHandler duplicateHandler = new DuplicateHandler(DEFAULT_ACTION.get(), POLICIES.get());
    try {
      jarBuilder.write(duplicateHandler);
    } catch (DuplicateEntryException e) {
      exit(1, "Refusing to write duplicate entry: %s", e.getMessage());
    } catch (IOException e) {
      exit(1, "Unexpected problem writing target jar %s: %s", target, e.getMessage());
    }
    exit(0);
  }

  private static void exit(int code) {
    exit(code, Optional.<String>absent());
  }

  private static void exit(int code, String message, Object... args) {
    exit(code, Optional.of(String.format(message, args)));
  }

  private static void exit(int code, Optional<String> message) {
    if (message.isPresent()) {
      PrintStream out = code == 0 ? System.out : System.err;
      out.println(message.get());
    }
    // We're a main - its fine to exit.
    // SUPPRESS CHECKSTYLE RegexpSinglelineJava
    System.exit(code);
  }
}
