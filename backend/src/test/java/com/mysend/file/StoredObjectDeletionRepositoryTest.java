package com.mysend.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storage-deletions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/storage-deletion-test-uploads"
})
class StoredObjectDeletionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Autowired
    private StoredObjectDeletionRepository deletions;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearQueue() {
        jdbc.sql("delete from storage_deletions").update();
    }

    @Test
    void queuesEachStorageKeyOnceAndTracksPendingCapacity() {
        deletions.enqueue("file-1.pdf", 2048, NOW);
        deletions.enqueue("file-1.pdf", 2048, NOW.plusSeconds(5));

        assertThat(deletions.countPending()).isEqualTo(1);
        assertThat(deletions.totalPendingBytes()).isEqualTo(2048);
        assertThat(deletions.oldestQueuedAt()).contains(NOW);
        assertThat(deletions.findDue(NOW, 10))
                .extracting(StoredObjectDeletion::storageKey)
                .containsExactly("file-1.pdf");
    }

    @Test
    void failedDeletionWaitsUntilItsNextAttempt() {
        deletions.enqueue("file-2.zip", 4096, NOW);
        deletions.markFailed(
                "file-2.zip",
                1,
                NOW.plusSeconds(30),
                "temporary outage"
        );

        assertThat(deletions.findDue(NOW.plusSeconds(29), 10)).isEmpty();
        assertThat(deletions.findDue(NOW.plusSeconds(30), 10))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.attempts()).isEqualTo(1);
                    assertThat(item.lastError()).isEqualTo("temporary outage");
                });

        deletions.markSucceeded("file-2.zip");
        assertThat(deletions.countPending()).isZero();
    }
}
