namespace java com.twitter.common.examples.distance.thriftjava
#@namespace scala com.twitter.common.examples.distance.thriftscala
namespace py com.twitter.common.examples.distance

/**
 * Structure for expressing distance measures: 8mm, 12 parsecs, etc.
 * Not so useful on its own.
 */
struct Distance {
  1: optional string Unit;
  2: required i64 Number;
}