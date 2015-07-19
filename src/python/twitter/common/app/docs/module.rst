.. _twitter.common.app.module :

`twitter.common.app.module`
===========================

.. py:module:: twitter.common.app.module

:ref:`twitter.common.app.module` provides a basic notion of injected `app.Modules`.

:class: AppModule(Singleton)
:method: setup_function()
:method: teardown_function()

You can build your own modules by inheriting from `AppModule`. You may want to overwrite the
`setup_function`/`teardown_function` to provide initialization and teardown functionality.

You may also want to fill in the the class level attribute `OPTIONS` to provide additional command
line flags to `twitter.common.app`.

Be sure to import `app` and call the `register_module` method::

  from optparse import Option

  class MyModule(AppModule):
    OPTIONS = {
        'verbose': Option('-v', action="store_true",
                       help="Turn on verbose output."),

  app.register_module(MyModule())


.. _twitter.common.app.modules :

`twitter.common.modules`
========================

.. warning::

   These are helpers that can be added to a program which uses `twitter.common.app`. Unfortunately,
   these are really only useful *if you are including the source alongside your application*.

   Do not depend upon these if you are using the distribution published on `PyPI
   <https://pypi.python.org/pypi/twitter.common.app>`_!

   There is no `twitter.common.app.modules` dist, and one will *not* exist until a successor to
   `twitter.common.app` is designed, built, and released. Due to some complexities with namespace
   packages, publishing `twitter.common.app.modules` as a dist will never resolve correctly. `PEP
   382 <https://www.python.org/dev/peps/pep-0382/>`_ contains the full set of details, and there was
   also some discussion in `r/1767/ <https://rbcommons.com/s/twitter/r/1767/>`_.

The following modules are included in `twitter.common.modules`

If you include these in your python_binary build target and use :ref:`twitter.common.app`, they
will automatically be initialized in your application at start-up.  One special one is `log` which
is always initialized if the code is included in your PEX.

.. py:module:: twitter.common.app.modules

`exceptions`
------------

See `src/python/twitter/common/app/modules:exceptions`

 * `BasicException` - If you raise an uncaught exception, log all thread stacks to
   twitter.common.log and stderr.
 * `ExceptionalThread`: use this in place of threading.Thread basically always.  the default
   behavior of threading.Thread is to not call sys.excepthook if it raises an exception, and
   ExceptionalThread enforces that it will.  It is also smart enough to log out all the current stack
   frames when it excepts which is an invaluable debugging tool.


`http`
---------------------


See `src/python/twitter/common/app/modules:http`

If --enable-http / --http-port is specified launches an internal http server and by default exports the following useful debugging endpoints:

 - `/threads` - current thread stacks
 - `/health` - returns 'OK' -- useful for mesos health checking
 - `/profile` - current profiling information if profiling is enabled

`http.server` contains the HttpServer class which is a wrapper around `bottle`.  If
you are using `twitter.common.app.modules:http`, use the `RootServer()` singleton if you'd like to
share.

`scribe_exceptions`
----------------------------------

See src/python/twitter/common/app/modules:scribe_exceptions

Uncaught exceptions get scribed (categories can be configured via
command-line or via app.configure).

`serverset`
--------------------------

See `src/python/twitter/common/app/modules:serverset`

Auto-join a Zookeeper serverset on start-up.


`vars`
------

See `src/python/twitter/common/app/modules:vars`

 - Adds the /vars endpoint to the http server launched in your
   application.
 - By default includes standard stuff like `sys.uptime` but can tell
   you which python interpreter you're running or what modules are
   loaded.
 - You can register application-specific observable metrics using
   ref:`metrics`.
