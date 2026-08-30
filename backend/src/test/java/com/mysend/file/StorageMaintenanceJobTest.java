package com.mysend.file;

import com.mysend.operations.OperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageMaintenanceJobTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private FileStore store;
    private RoomFileRepository files;
    private StoredObjectDeletionRepository deletions;
    private OperationalMetrics metrics;
    private StorageMaintenanceJob job;

    @BeforeEach
    void setUp() {
        store = mock(FileStore.class);
        files = mock(RoomFileRepository.class);
        deletions = mock(StoredObjectDeletionRepository.class);
        metrics = mock(OperationalMetrics.class);
        job = new StorageMaintenanceJob(
                store,
                files,
                deletions,
                new StorageProperties(
                        "local",
                        Duration.ofHours(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(15),
                        10L * 1024 * 1024 * 1024,
                        null
                ),
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void removesQueuedObjectsAndAcknowledgesTheQueue() throws IOException {
        StoredObjectDeletion deletion = deletion("file-1.pdf", 0);
        when(deletions.findDue(NOW, 100)).thenReturn(List.of(deletion));

        job.deleteQueuedObjects();

        verify(store).delete(deletion.storageKey());
        verify(deletions).markSucceeded(deletion.storageKey());
        verify(metrics).recordStoredObjectDeleted();
    }

    @Test
    void retriesFailedObjectDeletionWithBackoff() throws IOException {
        StoredObjectDeletion deletion = deletion("file-2.zip", 0);
        when(deletions.findDue(NOW, 100)).thenReturn(List.of(deletion));
        doThrow(new IOException("bucket unavailable"))
                .when(store)
                .delete(deletion.storageKey());

        job.deleteQueuedObjects();

        verify(deletions).markFailed(
                deletion.storageKey(),
                1,
                NOW.plusSeconds(2),
                "bucket unavailable"
        );
        verify(metrics).recordStorageDeletionFailure();
    }

    @Test
    void reconciliationQueuesOnlyOldUnreferencedObjects() throws IOException {
        FileStore.StoredObject orphan = new FileStore.StoredObject(
                "orphan.pdf",
                512,
                NOW.minus(Duration.ofHours(2))
        );
        FileStore.StoredObject referenced = new FileStore.StoredObject(
                "referenced.pdf",
                1024,
                NOW.minus(Duration.ofHours(2))
        );
        FileStore.StoredObject recent = new FileStore.StoredObject(
                "recent.pdf",
                2048,
                NOW.minus(Duration.ofMinutes(30))
        );
        when(store.list()).thenReturn(List.of(orphan, referenced, recent));
        when(files.existsByStorageKey(referenced.storageKey())).thenReturn(true);

        job.reconcileOrphanedObjects();

        verify(deletions).enqueue(orphan.storageKey(), orphan.sizeBytes(), NOW);
        verify(metrics).recordOrphanQueued();
        verify(deletions, never()).enqueue(referenced.storageKey(), referenced.sizeBytes(), NOW);
        verify(deletions, never()).enqueue(recent.storageKey(), recent.sizeBytes(), NOW);
    }

    private StoredObjectDeletion deletion(String key, int attempts) {
        return new StoredObjectDeletion(
                key,
                1024,
                NOW.minusSeconds(30),
                attempts,
                NOW,
                null
        );
    }
}
