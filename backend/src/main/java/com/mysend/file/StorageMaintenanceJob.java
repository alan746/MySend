package com.mysend.file;

import com.mysend.operations.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
public class StorageMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(StorageMaintenanceJob.class);
    private static final int DELETE_BATCH_SIZE = 100;

    private final FileStore store;
    private final RoomFileRepository files;
    private final StoredObjectDeletionRepository deletions;
    private final StorageProperties properties;
    private final OperationalMetrics metrics;
    private final Clock clock;

    public StorageMaintenanceJob(
            FileStore store,
            RoomFileRepository files,
            StoredObjectDeletionRepository deletions,
            StorageProperties properties,
            OperationalMetrics metrics,
            Clock clock
    ) {
        this.store = store;
        this.files = files;
        this.deletions = deletions;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mysend.storage.delete-interval:1m}")
    void deleteQueuedObjects() {
        var now = clock.instant();
        deletions.findDue(now, DELETE_BATCH_SIZE).forEach(deletion -> {
            try {
                store.delete(deletion.storageKey());
                deletions.markSucceeded(deletion.storageKey());
                metrics.recordStoredObjectDeleted();
            } catch (IOException exception) {
                int attempts = deletion.attempts() + 1;
                Duration retryDelay = Duration.ofSeconds(Math.min(
                        3600,
                        1L << Math.min(attempts, 11)
                ));
                deletions.markFailed(
                        deletion.storageKey(),
                        attempts,
                        now.plus(retryDelay),
                        exception.getMessage()
                );
                metrics.recordStorageDeletionFailure();
                log.warn(
                        "Stored object deletion failed for {} on attempt {}",
                        deletion.storageKey(),
                        attempts,
                        exception
                );
            }
        });
    }

    @Scheduled(fixedDelayString = "${mysend.storage.reconcile-interval:15m}")
    void reconcileOrphanedObjects() {
        var now = clock.instant();
        var cutoff = now.minus(properties.orphanGracePeriod());
        try {
            store.list().stream()
                    .filter(object -> !object.lastModified().isAfter(cutoff))
                    .filter(object -> !files.existsByStorageKey(object.storageKey()))
                    .filter(object -> !deletions.exists(object.storageKey()))
                    .forEach(object -> {
                        deletions.enqueue(object.storageKey(), object.sizeBytes(), now);
                        metrics.recordOrphanQueued();
                    });
        } catch (IOException exception) {
            metrics.recordStorageDeletionFailure();
            log.warn("Stored object reconciliation failed", exception);
        }
    }
}
