`twitter.common.string`
=======================

The most useful thing here is the `ScanfParser` for extracting
structured information from e.g. log lines or git tags.  a few
libraries in science use this e.g. `twitter.common.hdfs.fs`, `vert`,
and `mesos`. ::

    from twitter.common.string import ScanfParser
    PARSER = ScanfParser('%(project)s_R%(number)d')
    value = PARSER.parse('foo_R123')

    >>> value.project
    'foo'
    >>> value.number
    123

See also:

.. automodule:: twitter.common.string
   :members:

