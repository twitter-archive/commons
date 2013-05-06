namespace py gen.twitter.pants.buildtimestats.thrift

struct Stats {
  1: string json_string
}

service StatsCollector{
  void scribe_stats(1:string json_string)
}
