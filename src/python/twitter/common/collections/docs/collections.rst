`twitter.common.collections`
============================

.. py:module:: twitter.common.collections

Contains implementation of collection types ordereddict,
orderedset, and an O(1) ringbuffer. ::

    from twitter.common.collections import OrderedDict, OrderedSet, RingBuffer

`ordereddict`
-------------

.. py:class:: OrderedDict(dict)
.. py:method:: __init__(*args, **kwargs)

The `OrderedDict` has the same signature as regular dictionaries but
calling it with kwargs is not recommended as the order of initial
kwargs is undefined. Methods have the Big-O time as regular
dictionaries but are order aware. For example

`OrderedSet`
------------

.. py:class:: OrderedSet(collections.MutableSet)

Similarly the `OrderedSet` behaves just like regular set but adds
ordered semantics to iteration and addition/removal of items.

`RingBuffer`
------------

A ringbuffer behaves like a `collections.deque` but is limited in
length and has O(1) random access. Appends that exceed the max cause
older entries to fall out of the ringbuffer. ::

    >>> rr = RingBuffer(5)
    >>> rr
    RingBuffer([], size=5)
    >>> for i in xrange(0, 5):
    ...   rr.append(i)
    ...
    >>> rr
    RingBuffer([0, 1, 2, 3, 4], size=5)
    >>> for i in xrange(5, 8):
    ...   rr.append(i)
    ...
    >>> rr
    RingBuffer([3, 4, 5, 6, 7], size=5)
    >>> rr[1]
    4
    >>> rr[-2]
    6
