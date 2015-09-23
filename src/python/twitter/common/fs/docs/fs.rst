`twitter.commmon.fs`
====================

.. py:module:: twitter.common.fs

Provides a Hadoop file system wrapper. Assumes a local `hadoop` command line client is installed. Example usage::

    from twitter.common.fs import HDFSHelper

    hdfs = HDFSHelper('/etc/foo.conf')
    hdfs.cat('/users/wickman/derp.txt')
    hdfs.ls('/users/wickman')


.. autoclass:: twitter.common.fs.HDFSHelper
   :members:
