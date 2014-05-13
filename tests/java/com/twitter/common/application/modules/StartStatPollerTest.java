package com.twitter.common.application.modules;

import java.util.Properties;

import org.junit.Test;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.TimeSeriesRepository;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.BuildInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StartStatPollerTest extends EasyMockTest {
  @Test
  public void testStartStatPollerExecute() {
    ShutdownRegistry shutdownRegistry = createMock(ShutdownRegistry.class);
    TimeSeriesRepository repository = createMock(TimeSeriesRepository.class);

    Properties properties = new Properties();
    final Long gitRevisionNumber = 1404461016779713L;
    properties.setProperty(BuildInfo.Key.GIT_REVISION_NUMBER.value, gitRevisionNumber.toString());
    String gitRevision = "foo_branch";
    properties.setProperty(BuildInfo.Key.GIT_REVISION.value, gitRevision);
    BuildInfo buildInfo = new BuildInfo(properties);

    StatsModule.StartStatPoller poller =
        new StatsModule.StartStatPoller(shutdownRegistry, buildInfo, repository);

    repository.start(shutdownRegistry);
    control.replay();

    poller.execute();

    Stat<Long> gitRevisionNumberStat =
        Stats.getVariable(Stats.normalizeName(BuildInfo.Key.GIT_REVISION_NUMBER.value));
    assertEquals(gitRevisionNumber, gitRevisionNumberStat.read());

    Stat<String> gitRevisionStat =
        Stats.getVariable(Stats.normalizeName(BuildInfo.Key.GIT_REVISION.value));
    assertEquals(gitRevision, gitRevisionStat.read());

    Stat<String> gitBranchNameStat =
        Stats.getVariable(Stats.normalizeName(BuildInfo.Key.GIT_BRANCHNAME.value));
    assertNull(gitBranchNameStat);
  }
}
