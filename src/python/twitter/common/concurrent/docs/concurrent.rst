`twitter.common.concurrent`
===========================

.. py:module:: twitter.common.concurrent

This module includes a dependency on a 2.7.x backport of the 3.x
`concurrent.futures` library.

It provides utilities for asynchronous execution:

deadline
--------

.. py:function:: deadline(closure, timeout=Amount(150, Time.MILLISECONDS), daemon=False, propagate=False)

`deadline` runs a function `closure` within a timeout. The  timeout can either be a number of seconds or
an intance of the :mod:`twitter.common.quantity` `Amount` class.

defer
-----

.. py:function:: defer(closure, **kwargs)

`defer` runs a function in a separate thread after a delay::

    >>> from twitter.common.concurrent import defer
    >>> from twitter.common.quantity import Amount, Time
    >>> def func():
    ...   print "I ran"
    >>> defer(delayed, delay=Amount(3, Time.SECONDS))


EventMuxer
----------

`EventMuxer` is a threaded event multiplexer.

.. py:class:: EventMuxer(object)

Which accepts multiple events (see the `Event` class in Python's :mod:`threading` module).

.. py:method:: __init__(self, *events)

This class is primarily of interest in the situation where multiple Events could trigger an action,
but the specific one is not of interest. ::

    >>> from twitter.common.concurrent import EventMuxer
    >>> e1, e2 = threading.Event(), threading.Event()
    # will block until e1 or e2 is set or timeout expires:
    >>> EventMuxer(e1, e2).wait(timeout=5)
    # will block indefinitely until e1 or e2 is set:
    >>> EventMuxer(e1, e2).wait()


.. note:: `wait()` does **not** support re-entry; new objects should be instantiated as needed.
