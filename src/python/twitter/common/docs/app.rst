.. _twitter.common.app:

twitter.common.app
==================

.. py:module:: twitter.common.app
 
`app` provides a common framework for writing python applications.

Learning to write apps
----------------------

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

    app.main() # auto

You can also register application level options with::

    app.add_option(<stuff>)

and it uses the same format as `optparse.Option()`. You can set the
usage string and app name on the app object.::

  app.set_name("myscript")
  app.set_usage("myscript [options] /path/to/data")

and like the `optparse.OptionParser` you can use the error method to
exit your script.::

  app.error("Something bad happened!")
  
Your libraries can also do `app.add_option()`, and
when you run your application if you do `--long-help` instead of just
`--help`, you will see all the registered options for all libraries
included in your application.

It also has the concept of application Modules a la Guice, the one
permissible use of dependency injection in science using python, some
of which are described below.

You can also do subcommands using the `@app.command` decorator. 



