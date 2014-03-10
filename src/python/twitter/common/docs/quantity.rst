`quantity`
==========

.. py:module:: twitter.common.quantity

An implementation of the Java Quantity API::

    twitter.common.quantity import Amount, Time, Data

    TIMEOUT = Amount(5, Time.MINUTES)
    SLEEP = Amount(100, Time.MILLISECONDS)
    now = Amount(0, Time.SECONDS)
    while now < TIMEOUT:
      time.sleep(SLEEP.as_(Time.SECONDS))
      now += SLEEP
      
Same for `Data.BYTES`, `Data.MB`, `Data.GB`, etc. There are also basic parsers for '5h23m15s' and '512mb'
in `twitter.common.quantity.parse_simple`.
