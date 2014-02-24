####################################
Using Pants with Thrift Dependencies
####################################

`Apache Thrift <http://thrift.apache.org/>`_ is a popular framework for working with
data types and service interfaces. It uses an Interface Definition Language (IDL) to
define these types and interfaces. There are tools to generate code in "real" programming
languages from Thrift IDL files. Two programs, perhaps in different in different
programming languages, should be able to communicate over Thrift interfaces by using
this generated code.

Pants knows Thrift. For each Thrift file you use, your codebase has some ``BUILD`` targets
that represent generating "real" code from IDL code. You can write code in your favorite
language that imports the generated code. To make the import work, your code's
``BUILD`` target depends on the appropriate Thrift ``BUILD`` target. The details of
that Thrift target depend on whether the IDL file "lives" in your source tree or
somewhere else.

There are two ways to use thrift:

* **Same-repo** Use this in the source tree where the Thrift files "live".
  This approach also lets you publish an IDL jar which other source trees
  can use...
* **Consuming Thrift from Elsewhere** Use this in other source trees
  to consume thrift published from the Thrift file's "home" repo.

*********
Same-repo
*********

If the Thrift IDL files live in your source tree, you can define Java and/or Python
library targets by setting up *lang*\_thrift_library targets. Other targets can depend
on a *lang*\_thrift_library; the associated code can then import the generated code.

For a Java example::

    # Target defined in src/thrift/com/twitter/mybird/BUILD:
    java_thrift_library(name='mybird',
      # Specify dependencies for thrift IDL file includes.
      dependencies=[
        pants('src/thrift/com/twitter/otherbird'),
      ],
      sources=globs('*.thrift')
    )

Pants knows that before it compiles such a target, it must first generate Java
code from the Thrift IDL files. Users can
depend on this target like any other internal target. In this case, users would
add a dependency on ``pants('src/thrift/com/twitter/mybird')``.

To make this thrift available to other source trees, you might publish it.

.. TODO when we have a doc about publishing it, link to it

*******************************
Consuming Thrift from Elsewhere
*******************************

Not all Thrift IDL files live in your repo. Pants supports cross-repo IDL
sharing
through IDL-only jars. An IDL-only jar is simply a Java ``jar``
containing raw thrift IDL files. This allows users to depend on a versioned
artifact to generate and compile the IDL themselves.

For example, you could consume the ``mybird`` artifact published above
in a different repo.

.. TODO link to 3rdparty doc when it exists

Define the IDL jar version that will be used in your repo.
Use a ``thrift_jar`` dependencies for this; this target should live in
the ``3rdparty`` part of the source tree.
This ensures all users in your repo use a consistent version number. ::

    # Target defined in 3rdparty/jvm/com/twitter/BUILD
    # In jvm tree as jar dependency sharing mechanism is used.
    dependencies(name='mybird-thrift',
      dependencies=[
        thrift_jar(org='com.twitter', name='mybird-thrift', rev='1.0.0')
      ]
    )

Other targets can depend on
`pants('3rdparty/jvm/com/twitter:mybird-thrift')` without worrying
where the IDL-only jar comes from. For example::

    # Target defined in src/java/com/twitter/otherbird/BUILD
    java_library(name='otherbird'
      dependencies=[
        compiled_idl(
          # List of one or more IDL-only jar_library targets to compile.
          idl_deps=[
            pants('3rdparty/jvm/com/twitter:mybird-thrift'),
          ],
          # Use defaults unless you have a good reason not to.
          # compiler='thrift',
          # language='java',
          # rpc_style='finagle',
        ),
      ],
      sources=globs('*.java'),
    )

In the library where we want to use compiled classes defined in thrift IDL we
specify a ``compiled_idl`` dependency. This happens on the dependee side of the
relationship because dependees need full control over how they choose to
generate the code (e.g.: language, namespace mappings).

You can't *publish* an IDL jar via a ``compiled_idl``; it's for consuming
IDL jars that were published elsewhere.


******************************
Thrift Client & Server Example
******************************

Enough theoretical mumbo jumbo - let's build a thrift client & server with
pants! While the example is written in Java these same concepts apply to
other languages.

Thrift IDL
==========

Since we're writing a thrift service, let's start by defining the service
interface. As pants is our build tool we'll also define a ``BUILD`` file
with a target owning the sources. We define our thrift service in
``src/thrift/com/twitter/common/examples/pingpong/pingpong.thrift``.

.. include:: ../../../../../src/thrift/com/twitter/common/examples/pingpong/pingpong.thrift
  :code:

And a target owning the sources in
``src/thrift/com/twitter/common/examples/pingpong/BUILD``.

.. include:: ../../../../../src/thrift/com/twitter/common/examples/pingpong/BUILD
  :code:

Notice the target type is :ref:`bdict_java_thrift_library`, and this target
staked its claim to our pingpong thrift IDL file. JVM library targets
(e.g.: :ref:`bdict_java_library`, :ref:`bdict_scala_library`) that depend on
this target will simply see generated code from the IDL. Since no additional
options are specified we use the defaults; however, if we need more
control over how code is generated we control that through arguments provided
by :ref:`bdict_java_thrift_library`.

.. NOTE::
   While ``java_thrift_library`` implies Java as the generated code language,
   in reality it can generate code into a number of target languages via
   the ``language`` parameter (scala for example). For Python code, however,
   use :ref:`bdict_python_thrift_library`.

.. TODO(travis): How to specify the repo thrift gen defaults?
.. TODO(travis): Maybe we should show generating Java and Scala code?

So we can focus on the build itself, bare-bones thrift client & server code
are provided for this example. For details about writing thrift services,
see the `Apache Thrift site <http://thrift.apache.org/>`_.

Thrift Server
=============

Let's examine ``src/java/com/twitter/common/examples/pingpong_thrift/server/BUILD``
to understand how the server consumes thrift.

.. include:: ../../../../../src/java/com/twitter/common/examples/pingpong_thrift/server/BUILD
  :code:

Notice how two targets are defined for this server.
A :ref:`bdict_java_library` has been defined to own the server source code.
Since the server has dependencies those are defined too. Notice how the server
depends on both java and thrift. As a consumer we simply depend on targets
that provide things we need and pants figures out the rest.

A :ref:`bdict_jvm_binary` has also been defined, turning our library into
something runnable. For example, a ``server-bin`` bundle would contain a
jar with a manifest file specifying the main class and dependency classpath.
We can simply start the server locally with: ::

    ./pants goal run src/java/com/twitter/common/examples/pingpong_thrift/server:server-bin

If you find this interesting but really just care about implementing thrift
server in Scala or Python, very little changes. For Scala, your
:ref:`bdict_scala_library` would simply depend on the target that owns
your thrift IDL sources. Same with Python, but using
:ref:`bdict_python_library`. The key thing to remember is depending on thrift
is the same as any other dependency. By the time the thrift dependency is
used the IDL has already been converted into plain old source code.

Thrift Client
=============

Now that we have the server, let's build the client. The client lives in a
separate package with its own BUILD file
``src/java/com/twitter/common/examples/pingpong_thrift/client/BUILD``.

.. include:: ../../../../../src/java/com/twitter/common/examples/pingpong_thrift/client/BUILD
  :code:

Again we see two targets, a :ref:`bdict_java_library` that owns sources and
defines dependencies, and a :ref:`bdict_jvm_binary` to simplify running the
client. We can run the client locally with: ::

    ./pants goal run src/java/com/twitter/common/examples/pingpong_thrift/client:client-bin
