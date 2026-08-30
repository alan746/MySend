package com.mysend.operations;

import com.mysend.file.RoomFileRepository;
import com.mysend.file.StorageProperties;
import com.mysend.file.StoredObjectDeletionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private RoomFileRepository files;
    private StoredObjectDeletionRepository deletions;
    private OperationalMetrics metrics;

    @BeforeEach
    void setUp() {
        files = mock(RoomFileRepository.class);
        deletions = mock(StoredObjectDeletionRepository.class);
        metrics = new OperationalMetrics(
                new SimpleMeterRegistry(),
                files,
                deletions,
                new StorageProperties(
                        "s3",
                        Duration.ofHours(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(15),
                        1000,
                        null
                ),
                new OperationsProperties(
                        "token",
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void warnsWhenStorageCrossesTheConfiguredCapacity() {
        when(files.totalSizeBytes()).thenReturn(900L);
        when(deletions.totalPendingBytes()).thenReturn(100L);

        assertThat(metrics.snapshot().get("warnings"))
                .asInstanceOf(list(String.class))
                .contains("STORAGE_CAPACITY");
    }

    @Test
    void warnsWhenObjectDeletionHasBeenBackloggedTooLong() {
        when(deletions.oldestQueuedAt()).thenReturn(
                Optional.of(NOW.minus(Duration.ofMinutes(31)))
        );

        assertThat(metrics.snapshot().get("warnings"))
                .asInstanceOf(list(String.class))
                .contains("STORAGE_DELETION_BACKLOG");
    }

    @Test
    void recordsAHealthyCleanupRun() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics observed = new OperationalMetrics(
                registry,
                files,
                deletions,
                new StorageProperties(
                        "s3",
                        Duration.ofHours(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(15),
                        1000,
                        null
                ),
                new OperationsProperties(
                        "token",
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        observed.finishCleanup(observed.startCleanup());

        assertThat(observed.snapshot().get("cleanupLastSuccessAgeSeconds"))
                .isEqualTo(0L);
    }
}
