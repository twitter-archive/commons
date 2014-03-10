`metrics`
=========

.. py:module:: twitter.common.metrics

A python implementation of the Java/Scala metrics library that we use for exporting metrics of live servers.

Typical use::

    from twitter.common.metrics import LambdaGauge, AtomicGauge, RootMetrics
    metrics = RootMetrics()
    derps = AtomicGauge('derps', 0)
    metrics.register(LambdaGauge('now', lambda: time.time()))
    metrics.register(derps)
    derps.increment()

These will be scraped in a background thread by the `/vars` handler.  There's also the `Observable`
mix-in as an easier way to export metrics from entire classes.








   


