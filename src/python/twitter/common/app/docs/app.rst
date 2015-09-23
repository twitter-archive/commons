.. _twitter.common.app:

twitter.common.app
==================

.. py:module:: twitter.common.app

`app` provides a common framework for writing python applications.

Basic Usage
-----------

This is probably the most important thing to learn when developing
applications in `twitter.commons` using python.  Just include it in
your dependencies by adding to your BUILD file::

    dependencies = [
      pants("src/python/twitter/common/app"),
    ]

And use it to replace the `if __name__=='__main__` idiom ::

    from twitter.common import app

    def main(args, options):
      ...do stuff...

    app.main()

You can also register application level command line options with::

    app.add_option(<stuff>)

and it uses the same format as :mod:`optparse.Option`. You can set the
usage string and app name on the app object.::

  app.set_name("myscript")
  app.set_usage("myscript [options] /path/to/data")

and like the :mod:`optparse.OptionParser` you can use the error method to
exit your script.::

  app.error("Something bad happened!")

Putting all the pieces together might result in a basic command
line tool that looks like this::

    from twitter.common import app
    from myapi import records, process

    app.add_option("-v", "--verbose", action="store_true", default=False)
    app.add_option("--offset", type="int", default=0)

    def main(args, options):
      if options.offset:
        records.skip(options.offset)
      for record in records:
        if options.verbose:
          print record
        process(record)

    app.main()

More Advanced Usage
-------------------

But sometimes we write more sophisticated command-line tools; tools
that run many different commands each of which may take different
options. If you find your `main` function crowded with `if` statements
and your options badly need to be grouped you might try using the
`@app.command` decorator.

Instead of writing a single `main` entry point write multiple command
functions, each of which receives `args` and `options`. The decorator will expose
each one as a sub-command automatically::

    from twitter.common import app

    app.add_option('-v', '--verbose', help="Log to stdout")


    @app.command
    def init(args, options):
      print "running init..."


    @app.command
    def build(args, options):
      print "running build..."

    app.main()

Now your app will automatically require a subcommand to run. ::

  $ ./pants run src/python/twitter/myproject:myapp
  Must supply one of the following commands: build, init
  $ ./pants run src/python/twitter/myproject:myapp init
  running init...

But what about options? As before we can add global options directly
to the `app` object. But if we have options that only apply to a
particular subcommand we can use the `app.command_option` decorator::

    @app.command_option('-f', '--force', help="Force a rebuild")
    @app.command
    def build(args, options):
      print "running build..."

and discover those options by asking for help on the subcommand::

    ./pants run src/python/twitter/myproject:myapp build -h
    Options:
      -h, --help, --short-help
                            show this help message and exit.
      --long-help           show options from all registered modules, not just the
                            __main__ module.
      -v VERBOSE, --verbose=VERBOSE
                            Log to stdout

      For build only:
        -f FORCE, --force=FORCE
                            Force a rebuild


Using `app` With Libraries
--------------------------

Your libraries can also do `app.add_option()`, and when you run your
application if you do `--long-help` instead of just `--help`, you will
see all the registered options for all libraries included in your
application.

Commands defined in libraries are **not** registered in your
application. However if your application wants to run commands that
are defined in an external library you can use::

    from twitter.common import app
    from twitter.myproject import mymodule

    app.register_commands_from(mymodule)
