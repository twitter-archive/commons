The goal of this example is to help you learn how to best structure your code and configure pants to effectively manage dependencies within a single code repository.

Let's begin by surveying the echo example, located at `src/java/com/twitter/common/examples/echo`.

    $ ls -1 src/java/com/twitter/common/examples/echo
    BUILD
    EchoMain.java
    Echoer.java
    FileEchoer.java
    HadoopEchoer.java
    OWNERS
    README.md

Here we see an application called `EchoMain` that prints a string provided by an implementation of `Echoer`. Two implementations exist:

* `FileEchoer`, a file-based implementation that only depends on the interface, guava, and standard library.
* `HadoopEchoer`, a Hadoop-based implementation that depends on the interface, guava, and Hadoop.

Let's see an example run:

    $ ./pants goal clean-all run src/java/com/twitter/common/examples/echo:echo-bin \
      --jvm-run-args='com.twitter.common.examples.echo.HadoopEchoer'
    Using Echoer: com.twitter.common.examples.echo.HadoopEchoer
    # BEGIN hosts added by Network Connect

Now let's use the local file-based implementation.

    $ ./pants goal clean-all run src/java/com/twitter/common/examples/echo:echo-bin \
      --jvm-run-args='com.twitter.common.examples.echo.FileEchoer'
    Using Echoer: com.twitter.common.examples.echo.FileEchoer
    # BEGIN hosts added by Network Connect

And let's create a bundle. Notice how the archive is quite large because it includes all Hadoop dependencies.

    $ ./pants goal clean-all bundle src/java/com/twitter/common/examples/echo:echo-bin --bundle-archive=zip
    $ du -sh dist/echo-bin.zip
    5.1M	dist/echo-bin.zip
    $ ls -l dist/echo-bin-bundle/libs | wc -l
           8

While the application may work well for its current needs there are a number of issues with this current approach.

* Users must currently bundle the entire echoer, even if they do not need all available echo providers. This causes bloat in deploy artifacts and increases the risk of dependency conflicts.

* Developers may wish to implement a custom `Echoer`, as we'll do shortly. As a single `java_library` exposes the entire echoer, to get the interface users get all dependencies of that target, including Hadoop for example.

Pants provides solutions to these issues by allowing developers to structure their code and build targets such that dependencies are straightforward to manage and users have great flexibility in the construction of their bundles.

# Adding an Echoer

Let's put our developer hat on and extend this application with a new `Echoer`. Conveniently, one already exists that you can copy into place.

    # Paste the code below into StaticEchoer.java
    $ mkdir -p src/java/com/twitter/myapp/echo
    $ vi src/java/com/twitter/myapp/echo/StaticEchoer.java

    package com.twitter.common.myapp.echo;
    import com.twitter.common.examples.echo.Echoer;
    public class StaticEchoer implements Echoer {
      @Override
      public String echo() {
        return "tall cat is tall";
      }
    }

This simple implementation always returns the same string. Now, let's write the BUILD file for this library.

    # Paste the following into:
    $ vi src/java/com/twitter/myapp/echo/BUILD

    java_library(name='echo',
      dependencies=[
        # Necessary for Echoer interface - but with many fellow travelers!
        pants('src/java/com/twitter/common/examples/echo'),
      ],
      sources=globs('*.java'),
    )

    jvm_binary(name='echo-bin',
      main='com.twitter.common.examples.echo.EchoMain',
      dependencies=[pants(':echo')],
    )

Let's run our new echoer implementation and view the dependencies in our bundle. Notice how Hadoop is included even though we use nothing beyond the standard library.

    $ ./pants goal clean-all run src/java/com/twitter/myapp/echo:echo-bin \
      --jvm-run-args='com.twitter.common.myapp.echo.StaticEchoer'
    Using Echoer: com.twitter.common.myapp.echo.StaticEchoer
    tall cat is tall
    $ ./pants goal bundle src/java/com/twitter/myapp/echo:echo-bin --bundle-archive=zip
    $ du -sh dist/echo-bin.zip
    5.1M	dist/echo-bin.zip
    $ ls dist/echo-bin-bundle/libs/ | wc -l
           8

# Rule of thumb: Use a subpackage when adding large dependencies

Let's remind ourselves of the echoer source files:

    src/java/com/twitter/common/examples/echo/Echoer.java
    src/java/com/twitter/common/examples/echo/EchoMain.java
    src/java/com/twitter/common/examples/echo/FileEchoer.java
    src/java/com/twitter/common/examples/echo/HadoopEchoer.java

Hadoop and its transitive dependencies are quite large, and not critical to the echoer, making `HadoopEchoer` a great candidate to refactor into a subpackage. Let's move `HadoopEchoer` into a subpackage and expose it as a stand-alone library.

    $ mkdir src/java/com/twitter/common/examples/echo/hadoop
    $ mv src/java/com/twitter/common/examples/echo/HadoopEchoer.java \
        src/java/com/twitter/common/examples/echo/hadoop/
    # Update package to:
    #   package com.twitter.common.examples.echo.hadoop;
    # Add import:
    #   import com.twitter.common.examples.echo.Echoer;
    $ vi src/java/com/twitter/common/examples/echo/hadoop/HadoopEchoer.java
    # Add the following to:
    $ vi src/java/com/twitter/common/examples/echo/hadoop/BUILD

    java_library(name='hadoop',
      dependencies=[
        pants('3rdparty:hadoop-core'),
        pants('src/java/com/twitter/common/examples/echo'),
      ]
      sources=globs('*.java'),
    )

Now we can remove the `hadoop-core` dependency from `src/java/com/twitter/common/examples/echo`. Now `src/java/com/twitter/myapp/echo` no longer has a transitive dependency on Hadoop!

Let's create a bundle with `StaticEchoer` and see how much fat we've cut.

    $ ./pants goal clean-all run src/java/com/twitter/myapp/echo:echo-bin \
      --jvm-run-args='com.twitter.common.myapp.echo.StaticEchoer'
    Using Echoer: com.twitter.common.myapp.echo.StaticEchoer
    tall cat is tall
    $ ./pants goal bundle src/java/com/twitter/myapp/echo:echo-bin --bundle-archive=zip
    $ du -sh dist/echo-bin.zip
    1.9M	dist/echo-bin.zip
    $ ls dist/echo-bin-bundle/libs/ | wc -l
           6

Prior to this refactor our bundle was ~5 MB with 8 dependencies. By simply refactoring the Hadoop-based functionality into an optional library we've shrunk the application bundle.

Before moving on, let's remember `FileEchoer` is still bundled with the interface. As `FileEchoer` only depends on guava and the standard library there's no harm in combining them in a single target. Use your judgement for how thin to define your targets.
