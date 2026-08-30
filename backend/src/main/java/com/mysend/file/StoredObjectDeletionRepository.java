package com.mysend.file;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class StoredObjectDeletionRepository {

    private final JdbcClient jdbc;

    public StoredObjectDeletionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(String storageKey, long sizeBytes, Instant now) {
        try {
            jdbc.sql("""
                            insert into storage_deletions (
                                storage_key, size_bytes, queued_at_ms,
                                attempts, next_attempt_at_ms, last_error
                            ) values (
                                :storageKey, :sizeBytes, :now, 0, :now, null
                            )
                            """)
                    .param("storageKey", storageKey)
                    .param("sizeBytes", sizeBytes)
                    .param("now", now.toEpochMilli())
                    .update();
        } catch (DuplicateKeyException ignored) {
        }
    }

    public List<StoredObjectDeletion> findDue(Instant now, int limit) {
        return jdbc.sql("""
                        select * from storage_deletions
                        where next_attempt_at_ms <= :now
                        order by next_attempt_at_ms, queued_at_ms
                        limit :limit
                        """)
                .param("now", now.toEpochMilli())
                .param("limit", limit)
                .query(StoredObjectDeletionRepository::mapDeletion)
                .list();
    }

    public boolean exists(String storageKey) {
        return jdbc.sql("""
                        select count(*) from storage_deletions
                        where storage_key = :storageKey
                        """)
                .param("storageKey", storageKey)
                .query(Integer.class)
                .single() > 0;
    }

    public void markSucceeded(String storageKey) {
        jdbc.sql("delete from storage_deletions where storage_key = :storageKey")
                .param("storageKey", storageKey)
                .update();
    }

    public void markFailed(
            String storageKey,
            int attempts,
            Instant nextAttemptAt,
            String lastError
    ) {
        jdbc.sql("""
                        update storage_deletions
                        set attempts = :attempts,
                            next_attempt_at_ms = :nextAttemptAt,
                            last_error = :lastError
                        where storage_key = :storageKey
                        """)
                .param("attempts", attempts)
                .param("nextAttemptAt", nextAttemptAt.toEpochMilli())
                .param("lastError", truncate(lastError))
                .param("storageKey", storageKey)
                .update();
    }

    public long countPending() {
        return jdbc.sql("select count(*) from storage_deletions")
                .query(Long.class)
                .single();
    }

    public long totalPendingBytes() {
        return jdbc.sql("select coalesce(sum(size_bytes), 0) from storage_deletions")
                .query(Long.class)
                .single();
    }

    public Optional<Instant> oldestQueuedAt() {
        return jdbc.sql("select min(queued_at_ms) from storage_deletions")
                .query(Long.class)
                .optional()
                .map(Instant::ofEpochMilli);
    }

    private static StoredObjectDeletion mapDeletion(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new StoredObjectDeletion(
                resultSet.getString("storage_key"),
                resultSet.getLong("size_bytes"),
                Instant.ofEpochMilli(resultSet.getLong("queued_at_ms")),
                resultSet.getInt("attempts"),
                Instant.ofEpochMilli(resultSet.getLong("next_attempt_at_ms")),
                resultSet.getString("last_error")
        );
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
