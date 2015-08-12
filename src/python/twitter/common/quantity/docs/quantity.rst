`twitter.common.quantity`
=========================

.. py:module:: twitter.common.quantity

An implementation of the Java Quantity API - a class that is instantiated with pre-defined units of measure:


.. autoclass:: twitter.common.quantity.Amount

The predefined units are `Time` and `Data`. Sample usage looks like::

    from twitter.common.quantity import Amount, Time, Data

    TIMEOUT = Amount(5, Time.MINUTES)
    SLEEP = Amount(100, Time.MILLISECONDS)
    now = Amount(0, Time.SECONDS)
    while now < TIMEOUT:
      time.sleep(SLEEP.as_(Time.SECONDS))
      now += SLEEP


See also `Data.BYTES`, `Data.MB`, `Data.GB`, etc. There are also basic
parsers for times like '5h23m15s' and sizes like '512mb' in
`twitter.common.quantity.parse_simple`.


.. automodule:: twitter.common.quantity.parse_simple
  :members:
