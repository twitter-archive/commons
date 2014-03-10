`rpc`
=====

.. py:module:: twitter.common.rpc
               
If you want to speak thrift or finaglized-thrift::

    from twitter.common.rpc import make_client

Basic usage::

    make_client(UserService, 'localhost', 9999)

To make an SSL socket server (see also make_server)::
  
    make_client(UserService, 'smf1-amk-25-sr1.prod.twitter.com', 9999,
                      connection=TSocket.TSSLServerSocket,
                      ca_certs=...)

To make a finagle client::
    make_client(UserService, 'localhost', 9999,
                      protocol=TFinagleProtocol)

And one with a `client_id`::

    make_client(UserService, 'localhost', 9999,
                      protocol=functools.partial(TFinagleProtocol, client_id="test_client"))

This is equivalent to::
  
    make_client(UserService, 'localhost', 9999,
                      protocol=TFinagleProtocolWithClientId("test_client")))

.. note:: Pants supports python thrift code generation.  if you have a .thrift file, just wrap it
          with a python_thrift_library(...) definition.  see `the styleguide <styleguide.html>`_
          for the idioms around namespacing thrift in your code.

