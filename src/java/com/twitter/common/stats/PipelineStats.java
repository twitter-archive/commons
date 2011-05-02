// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.collections.Pair;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the latency of different pipeline stages in a process.
 *
 * @author William Farner
 */
public class PipelineStats {
  private static final String FULL_PIPELINE_NAME = "full";

  private final Time precision;
  private final Clock clock;

  private final Map<String, SlidingStats> stages;

  /**
   * Creates a new pipeline tracker with the given pipeline name and stages. The stage name "full"
   * is reserved to represent the duration of the entire pipeline.
   *
   * @param pipelineName Name of the pipeline.
   * @param stages Stage names.
   * @param precision Precision for time interval recording.
   */
  public PipelineStats(String pipelineName, Set<String> stages, Time precision) {
    this(pipelineName, stages, Clock.SYSTEM_CLOCK, precision);
  }

  @VisibleForTesting
  PipelineStats(String pipelineName, Set<String> stages, Clock clock, Time precision) {
    MorePreconditions.checkNotBlank(pipelineName);
    MorePreconditions.checkNotBlank(stages);
    Preconditions.checkArgument(!stages.contains(FULL_PIPELINE_NAME));

    this.clock = Preconditions.checkNotNull(clock);
    this.precision = Preconditions.checkNotNull(precision);

    this.stages = Maps.newHashMap();
    for (String stage : stages) {
      this.stages.put(stage, new SlidingStats(
          String.format("%s_%s", pipelineName, stage), precision.toString()));
    }
    this.stages.put(FULL_PIPELINE_NAME, new SlidingStats(
        String.format("%s_%s", pipelineName, FULL_PIPELINE_NAME), precision.toString()));
  }

  private void record(Snapshot snapshot) {
    for (Pair<String, Long> stage : snapshot.stages) {
      stages.get(stage.getFirst()).accumulate(stage.getSecond());
    }
  }

  public Snapshot newSnapshot() {
    return new Snapshot(this);
  }

  @VisibleForTesting
  public SlidingStats getStatsForStage(String stage) {
    return stages.get(stage);
  }

  public class Snapshot {
    private final List<Pair<String, Long>> stages = Lists.newLinkedList();
    private final PipelineStats parent;

    private String currentStage;
    private long startTime;
    private long ticker;

    private Snapshot(PipelineStats parent) {
      this.parent = parent;
    }

    /**
     * Records the duration for the current pipeline stage, and advances to the next stage. The
     * stage name must be one of the stages specified in the constructor.
     *
     * @param stageName Name of the stage to enter.
     */
    public void start(String stageName) {
      record(Preconditions.checkNotNull(stageName));
    }

    private void record(String stageName) {
      long now = Amount.of(clock.nowNanos(), Time.NANOSECONDS).as(precision);
      if (currentStage != null) {
        stages.add(Pair.of(currentStage, now - ticker));
      } else {
        startTime = now;
      }

      if (stageName == null) stages.add(Pair.of(FULL_PIPELINE_NAME, now - startTime));

      ticker = now;
      currentStage = stageName;
    }

    /**
     * Stops the pipeline, recording the interval for the last registered stage.
     * This is the same as calling {@link #start(String)} with {@code null};
     *
     */
    public void end() {
      record(null);
      parent.record(this);
    }
  }
}
