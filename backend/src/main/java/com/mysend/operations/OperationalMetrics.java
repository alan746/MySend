package com.mysend.operations;

import com.mysend.file.RoomFileRepository;
import com.mysend.file.StorageProperties;
import com.mysend.file.StoredObjectDeletionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OperationalMetrics {

    private final RoomFileRepository files;
    private final StoredObjectDeletionRepository deletions;
    private final StorageProperties storage;
    private final OperationsProperties operations;
    private final Clock clock;
    private final MeterRegistry registry;
    private final AtomicLong lastCleanupSuccess = new AtomicLong(-1);
    private final AtomicLong maximumPurgeLagSeconds = new AtomicLong(0);
    private final Counter cleanupFailures;
    private final Counter roomsPurged;
    private final Counter storageDeletionFailures;
    private final Counter storedObjectsDeleted;
    private final Counter orphanedObjectsQueued;
    private final Timer cleanupDuration;

    public OperationalMetrics(
            MeterRegistry registry,
            RoomFileRepository files,
            StoredObjectDeletionRepository deletions,
            StorageProperties storage,
            OperationsProperties operations,
            Clock clock
    ) {
        this.files = files;
        this.deletions = deletions;
        this.storage = storage;
        this.operations = operations;
        this.clock = clock;
        this.registry = registry;
        this.cleanupFailures = registry.counter("mysend.cleanup.failures");
        this.roomsPurged = registry.counter("mysend.cleanup.rooms.purged");
        this.storageDeletionFailures = registry.counter("mysend.storage.deletion.failures");
        this.storedObjectsDeleted = registry.counter("mysend.storage.objects.deleted");
        this.orphanedObjectsQueued = registry.counter("mysend.storage.orphans.queued");
        this.cleanupDuration = registry.timer("mysend.cleanup.duration");

        Gauge.builder("mysend.storage.live.bytes", files, RoomFileRepository::totalSizeBytes)
                .baseUnit("bytes")
                .register(registry);
        Gauge.builder(
                        "mysend.storage.deletion.pending",
                        deletions,
                        StoredObjectDeletionRepository::countPending
                )
                .register(registry);
        Gauge.builder(
                        "mysend.storage.deletion.pending.bytes",
                        deletions,
                        StoredObjectDeletionRepository::totalPendingBytes
                )
                .baseUnit("bytes")
                .register(registry);
        Gauge.builder("mysend.storage.capacity.ratio", this, value -> value.capacityRatio())
                .register(registry);
        Gauge.builder(
                        "mysend.storage.deletion.oldest.age.seconds",
                        this,
                        value -> value.oldestDeletionAge().toSeconds()
                )
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(
                        "mysend.cleanup.last.success.age.seconds",
                        this,
                        value -> value.lastCleanupSuccessAge().toSeconds()
                )
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(
                        "mysend.cleanup.maximum.purge.lag.seconds",
                        maximumPurgeLagSeconds,
                        AtomicLong::get
                )
                .baseUnit("seconds")
                .register(registry);
    }

    public Timer.Sample startCleanup() {
        return Timer.start(registry);
    }

    public void finishCleanup(Timer.Sample sample) {
        sample.stop(cleanupDuration);
        lastCleanupSuccess.set(clock.instant().getEpochSecond());
    }

    public void recordCleanupFailure() {
        cleanupFailures.increment();
    }

    public void recordRoomPurged(Duration lag) {
        roomsPurged.increment();
        maximumPurgeLagSeconds.accumulateAndGet(lag.toSeconds(), Math::max);
    }

    public void recordStorageDeletionFailure() {
        storageDeletionFailures.increment();
    }

    public void recordStoredObjectDeleted() {
        storedObjectsDeleted.increment();
    }

    public void recordOrphanQueued() {
        orphanedObjectsQueued.increment();
    }

    public Map<String, Object> snapshot() {
        long liveBytes = files.totalSizeBytes();
        long pendingBytes = deletions.totalPendingBytes();
        Duration cleanupAge = lastCleanupSuccessAge();
        Duration deletionAge = oldestDeletionAge();
        List<String> warnings = java.util.stream.Stream.of(
                        capacityRatio() >= 1 ? "STORAGE_CAPACITY" : null,
                        cleanupAge.compareTo(operations.cleanupStaleAfter()) > 0
                                ? "CLEANUP_STALE" : null,
                        deletionAge.compareTo(operations.deletionBacklogWarning()) > 0
                                ? "STORAGE_DELETION_BACKLOG" : null
                )
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", warnings.isEmpty() ? "OK" : "WARNING");
        result.put("warnings", warnings);
        result.put("liveStorageBytes", liveBytes);
        result.put("pendingDeletionBytes", pendingBytes);
        result.put("pendingDeletions", deletions.countPending());
        result.put("capacityRatio", capacityRatio());
        result.put("cleanupLastSuccessAgeSeconds", cleanupAge.toSeconds());
        result.put("maximumPurgeLagSeconds", maximumPurgeLagSeconds.get());
        result.put("oldestDeletionAgeSeconds", deletionAge.toSeconds());
        return Map.copyOf(result);
    }

    private double capacityRatio() {
        if (storage.capacityWarningBytes() <= 0) {
            return 0;
        }
        return (double) (files.totalSizeBytes() + deletions.totalPendingBytes())
                / storage.capacityWarningBytes();
    }

    private Duration oldestDeletionAge() {
        Instant now = clock.instant();
        return deletions.oldestQueuedAt()
                .map(queuedAt -> nonNegative(Duration.between(queuedAt, now)))
                .orElse(Duration.ZERO);
    }

    private Duration lastCleanupSuccessAge() {
        long epochSecond = lastCleanupSuccess.get();
        if (epochSecond < 0) {
            return operations.cleanupStaleAfter().plusSeconds(1);
        }
        return nonNegative(Duration.between(
                Instant.ofEpochSecond(epochSecond),
                clock.instant()
        ));
    }

    private static Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }
}
