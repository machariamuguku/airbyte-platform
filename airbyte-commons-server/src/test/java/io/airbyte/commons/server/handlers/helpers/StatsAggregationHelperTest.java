/*
 * Copyright (c) 2020-2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.server.handlers.helpers;

import static io.airbyte.commons.server.handlers.helpers.StatsAggregationHelper.wasBackfilled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.airbyte.commons.server.handlers.helpers.StatsAggregationHelper.StreamStatsRecord;
import io.airbyte.config.StreamSyncStats;
import io.airbyte.config.SyncStats;
import io.airbyte.protocol.models.SyncMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatsAggregationHelperTest {

  private static final StreamSyncStats STREAM_SYNC_STATS_1 = new StreamSyncStats()
      .withStats(new SyncStats()
          .withRecordsEmitted(40L)
          .withBytesEmitted(30L)
          .withRecordsCommitted(20L)
          .withBytesCommitted(10L))
      .withWasBackfilled(true);

  private static final StreamSyncStats STREAM_SYNC_STATS_2 = new StreamSyncStats()
      .withStats(new SyncStats()
          .withRecordsEmitted(400L)
          .withBytesEmitted(300L)
          .withRecordsCommitted(200L)
          .withBytesCommitted(100L))
      .withWasBackfilled(false);

  private static final StreamSyncStats STREAM_SYNC_STATS_3 = new StreamSyncStats()
      .withStats(new SyncStats()
          .withRecordsEmitted(4000L)
          .withBytesEmitted(3000L)
          .withRecordsCommitted(2000L)
          .withBytesCommitted(1000L));

  private static final StreamSyncStats STREAM_SYNC_STATS_WITH_NULL_FIELDS = new StreamSyncStats()
      .withStats(new SyncStats()
          .withRecordsEmitted(null)
          .withBytesEmitted(30000L)
          .withRecordsCommitted(null)
          .withBytesCommitted(10000L));

  @Test
  void testAggregatedStatsFullRefresh() {
    StreamStatsRecord aggregatedStats = StatsAggregationHelper.getAggregatedStats(
        SyncMode.FULL_REFRESH,
        List.of(
            STREAM_SYNC_STATS_1,
            STREAM_SYNC_STATS_2));

    assertEquals(400L, aggregatedStats.recordsEmitted());
    assertEquals(300L, aggregatedStats.bytesEmitted());
    assertEquals(200L, aggregatedStats.recordsCommitted());
    assertEquals(100L, aggregatedStats.bytesCommitted());
  }

  @Test
  void testIncremental() {
    StreamStatsRecord aggregatedStats = StatsAggregationHelper.getAggregatedStats(
        SyncMode.INCREMENTAL,
        List.of(
            STREAM_SYNC_STATS_1,
            STREAM_SYNC_STATS_2));

    assertEquals(440L, aggregatedStats.recordsEmitted());
    assertEquals(330L, aggregatedStats.bytesEmitted());
    assertEquals(220L, aggregatedStats.recordsCommitted());
    assertEquals(110L, aggregatedStats.bytesCommitted());
  }

  @Test
  void testIncrementalWithNullStats() {
    StreamStatsRecord aggregatedStats = StatsAggregationHelper.getAggregatedStats(
        SyncMode.INCREMENTAL,
        List.of(
            STREAM_SYNC_STATS_1,
            STREAM_SYNC_STATS_WITH_NULL_FIELDS,
            STREAM_SYNC_STATS_2));

    assertEquals(440L, aggregatedStats.recordsEmitted());
    assertEquals(30330L, aggregatedStats.bytesEmitted());
    assertEquals(220L, aggregatedStats.recordsCommitted());
    assertEquals(10110L, aggregatedStats.bytesCommitted());
  }

  @Test
  void testFullRefreshWithNullStats() {
    StreamStatsRecord aggregatedStats = StatsAggregationHelper.getAggregatedStats(
        SyncMode.FULL_REFRESH,
        List.of(
            STREAM_SYNC_STATS_1,
            STREAM_SYNC_STATS_2,
            STREAM_SYNC_STATS_WITH_NULL_FIELDS));

    assertEquals(0, aggregatedStats.recordsEmitted());
    assertEquals(30000L, aggregatedStats.bytesEmitted());
    assertEquals(0, aggregatedStats.recordsCommitted());
    assertEquals(10000L, aggregatedStats.bytesCommitted());
  }

  @Test
  void testStreamWasBackfilled() {
    Optional<Boolean> wasBackfilled = wasBackfilled(List.of(STREAM_SYNC_STATS_1, STREAM_SYNC_STATS_2, STREAM_SYNC_STATS_3));
    assertTrue(wasBackfilled.isPresent());
    assertTrue(wasBackfilled.get());
  }

  @Test
  void testStreamWasNotBackfilled() {
    Optional<Boolean> wasBackfilled = wasBackfilled(List.of(STREAM_SYNC_STATS_2, STREAM_SYNC_STATS_3));
    assertTrue(wasBackfilled.isPresent());
    assertFalse(wasBackfilled.get());
  }

  @Test
  void testBackfillNotSpecified() {
    Optional<Boolean> wasBackfilled = wasBackfilled(List.of(STREAM_SYNC_STATS_3));
    assertTrue(wasBackfilled.isEmpty());
  }

}
