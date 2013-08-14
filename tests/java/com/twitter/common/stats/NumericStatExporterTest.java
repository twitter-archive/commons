// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.mockito.MockitoTest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@code NumericStatExporter}
 */
public class NumericStatExporterTest extends MockitoTest {
  private static final Amount<Long, Time> TEST_EXPORT_INTERVAL = Amount.of(1L, Time.MINUTES);
  private static final String MOCK_STAT_NAME = "NumericStatExporterTest_mock_stat";
  private static final int MOCK_STAT_READ_VALUE = 0;
  private static final int MOCK_STAT_SAMPLED_VALUE = 1;

  @Mock
  private Closure<Map<String, ? extends Number>> mockExportSink;
  @Mock
  private ScheduledExecutorService mockExecutor;
  @Mock
  private ShutdownRegistry mockShutdownRegistry;
  @Mock
  private RecordingStat<Integer> mockRecordingStat;

  @Captor
  private ArgumentCaptor<Runnable> runnableCaptor;
  @Captor
  private ArgumentCaptor<Command> commandCaptor;
  @Captor
  private ArgumentCaptor<Map<String, ? extends Number>> statReadValueMapCaptor;


  private NumericStatExporter numericStatExporter;

  @Before
  public void setUp() {
    when(mockRecordingStat.getName()).thenReturn(MOCK_STAT_NAME);
    when(mockRecordingStat.read()).thenReturn(MOCK_STAT_READ_VALUE);
    when(mockRecordingStat.sample()).thenReturn(MOCK_STAT_SAMPLED_VALUE);
    Stats.export(mockRecordingStat);

    numericStatExporter
        = new NumericStatExporter(mockExportSink, mockExecutor, TEST_EXPORT_INTERVAL);
  }

  @Test
  public void testStartMethodScheduleExport() {
    numericStatExporter.start(mockShutdownRegistry);

    // Verify the executor is scheduled properly.
    verify(mockExecutor).scheduleAtFixedRate(runnableCaptor.capture(),
        anyLong(), anyLong(), Matchers.<TimeUnit>anyObject());
    // Verify the behavior of the schedule runnable.
    runnableCaptor.getValue().run();
    verify(mockExportSink).execute(statReadValueMapCaptor.capture());
    // Verify stat reading behavior.
    assertEquals(MOCK_STAT_READ_VALUE, statReadValueMapCaptor.getValue().get(MOCK_STAT_NAME));
  }

  @Test
  public void testStartMethodShutdownRegistryFinalSampleAndExport() {
    numericStatExporter.start(mockShutdownRegistry);

    // Verify the shutdown registry is called.
    verify(mockShutdownRegistry).addAction(commandCaptor.capture());
    // Verify the behavior of the shutdown registry command.
    commandCaptor.getValue().execute();

    // The shutdown command calls stop(), which we'll test separately.

    // Now verifies the final sample and export behavior.
    verify(mockExportSink).execute(statReadValueMapCaptor.capture());
    // Verify stat sampling and reading behavior.
    assertEquals(MOCK_STAT_SAMPLED_VALUE, statReadValueMapCaptor.getValue().get(MOCK_STAT_NAME));
  }

  @Test
  public void testStopMethodAwaitTerminationReturnsFast() throws Exception {
    when(mockExecutor.awaitTermination(anyLong(), Matchers.<TimeUnit>anyObject()))
        .thenReturn(true);
    numericStatExporter.stop();
    verify(mockExecutor).awaitTermination(eq(1L), eq(TimeUnit.SECONDS));
    verifyNoMoreInteractions(mockExecutor);
  }

  @Test
  public void testStopMethodAwaitTerminationReturnsSlowly() throws Exception {
    when(mockExecutor.awaitTermination(anyLong(), Matchers.<TimeUnit>anyObject()))
        .thenReturn(false);
    numericStatExporter.stop();
    verify(mockExecutor, times(2)).awaitTermination(eq(1L), eq(TimeUnit.SECONDS));
    verify(mockExecutor).shutdownNow();
    verifyNoMoreInteractions(mockExecutor);
  }

  @Test
  public void testStopMethodAwaitTerminationInterrupted() throws Exception {
    when(mockExecutor.awaitTermination(anyLong(), Matchers.<TimeUnit>anyObject()))
        .thenThrow(new InterruptedException("mock failure"));
    numericStatExporter.stop();
    verify(mockExecutor).awaitTermination(eq(1L), eq(TimeUnit.SECONDS));
    verify(mockExecutor).shutdownNow();
    verifyNoMoreInteractions(mockExecutor);
    // We need to reset the thread's interrupt flag so other tests who uses certain
    // concurrent calls like latches and various waits wouldn't fail.
    Thread.currentThread().interrupted();
  }
}

