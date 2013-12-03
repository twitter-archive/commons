package com.twitter.common.metrics

import com.twitter.finagle.stats.MetricsStatsReceiver

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class MetricsStatsReceiverTest extends FunSuite {
  val rootReceiver = new MetricsStatsReceiver()

  def read(metrics: MetricsStatsReceiver)(name: String): Object =
    metrics.registry.sample().get(name)

  def readInRoot(name: String) = read(rootReceiver)(name)

  test("store and read gauge into the root StatsReceiver") {
    val x = 1.5f
    rootReceiver.addGauge("my_gauge")(x)
    readInRoot("my_gauge") === x
  }

  test("store and read counter into the root StatsReceiver") {
    rootReceiver.counter("my_counter").incr()
    readInRoot("my_counter") === 1
  }

  test("separate gauge/stat/metric between detached Metrics and root Metrics") {
    val detachedReceiver = new MetricsStatsReceiver(Metrics.createDetached())
    detachedReceiver.addGauge("xxx")(1.0f)
    rootReceiver.addGauge("xxx")(2.0f)
    assert(read(detachedReceiver)("xxx") != read(rootReceiver)("xxx"))
  }
}
