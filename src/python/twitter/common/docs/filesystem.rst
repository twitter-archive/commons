`fs`
====

.. py:module:: twitter.common.fs

Provides a Hadoop file system wrapper. Assummes local `hadoop` client is installed::

    from twitter.common.fs import HDFSHelper

    hdfs = HDFSHelper('/etc/foo.conf')
    hdfs.cat('/users/wickman/derp.txt')
    hdfs.ls('/users/wickman')
