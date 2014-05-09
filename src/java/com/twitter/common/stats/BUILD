# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

java_library(name = 'stat',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stat',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [],
  sources = ['Stat.java'],
)

java_library(name = 'stats-provider',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stats-provider',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants(':stat'),
  ],
  sources = [
    'StatsProvider.java',
  ],
)

java_library(name = 'stat-registry',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stat-registry',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants(':stat'),
  ],
  sources = [
    'RecordingStat.java',
    'StatRegistry.java'
  ],
)

java_library(name = 'stats',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stats',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants(':stat-registry'),
    pants(':stats-provider'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/util:sampler'),
  ],
  sources = [
    'Elapsed.java',
    'MovingAverage.java',
    'MovingWindowDelta.java',
    'NumericStatExporter.java',
    'Percentile.java',
    'PipelineStats.java',
    'Ratio.java',
    'Rate.java',
    'RecordingStatImpl.java',
    'RequestStats.java',
    'SlidingStats.java',
    'SampledStat.java',
    'StatImpl.java',
    'Stats.java',
  ],
)

java_library(name = 'jvm',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stats-jvm',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants(':stats'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = [ 'JvmStats.java' ],
)

java_library(name = 'time-series',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stats-time-series',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants(':stat-registry'),
    pants(':stats'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = [
    'TimeSeries.java',
    'TimeSeriesRepository.java',
    'TimeSeriesRepositoryImpl.java',
  ],
)

java_library(name = 'util',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'stats-util',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('src/java/com/twitter/common/util:util'),
  ],
  sources = [
    'ApproximateHistogram.java',
    'CounterMap.java',
    'CounterMapWithTopKey.java',
    'Entropy.java',
    'Histogram.java',
    'Histograms.java',
    'Precision.java',
    'PrintableHistogram.java',
    'ReservoirSampler.java',
    'Significance.java',
    'Statistics.java',
    'StatisticsInterface.java',
    'Windowed.java',
    'WindowedApproxHistogram.java',
    'WindowedHistogram.java',
    'WindowedStatistics.java',
  ],
)
