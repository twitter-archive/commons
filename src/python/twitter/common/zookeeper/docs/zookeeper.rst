`twitter.common.zookeeper`
==========================


Contains two wrappers, one for C ZooKeeper, one for Kazoo. `Shortly,
this will only be the Kazoo library
<https://github.com/twitter/commons/issues/317>`_.

.. note:: Our use of `twitter.common.zookeeper.client` is slowly being
          deprecated in favor of the Kazoo pure python Zookeeper client.

.. automodule:: twitter.common.zookeeper
   :members:


.. I'm using autoclass here to explicitly provide a signature without an ugly default value for the "logger" param

.. autoclass:: twitter.common.zookeeper.client.ZooKeeper(servers=None, timeout_secs=None, watch=None, max_reconnects=None, authentication=None, logger=log.debug)
   :members:
