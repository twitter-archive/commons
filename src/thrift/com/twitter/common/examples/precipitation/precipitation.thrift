namespace java com.twitter.common.examples.precipitation.thriftjava
#@namespace scala com.twitter.common.examples.precipitation.thriftscala
namespace py com.twitter.common.examples.precipitation

include "com/twitter/common/examples/distance/distance.thrift"

/**
 * Structure for recording weather events, e.g., 8mm of rain.
 */
struct Precipitation {
  1: optional string substance = "rain";
  2: optional distance.Distance distance;
}