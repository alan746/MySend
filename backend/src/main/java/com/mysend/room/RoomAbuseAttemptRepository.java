package com.mysend.room;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RoomAbuseAttemptRepository {

    private static final int LOCK_STRIPES = 64;

    private final JdbcClient jdbc;

    public RoomAbuseAttemptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Instant> acquire(
            String subjectHash,
            RoomAbuseAction action,
            Duration window,
            int limit,
            Instant now
    ) {
        int stripe = Math.floorMod(subjectHash.hashCode(), LOCK_STRIPES);
        jdbc.sql("select stripe_id from room_abuse_lock_stripes where stripe_id = :stripe for update")
                .param("stripe", stripe)
                .query(Integer.class)
                .single();

        Instant since = now.minus(window);
        AttemptWindow attempts = jdbc.sql("""
                        select count(*) as attempt_count,
                               min(attempted_at_ms) as oldest_attempt_ms
                        from room_abuse_attempts
                        where subject_hash = :subjectHash
                          and action = :action
                          and attempted_at_ms >= :since
                        """)
                .param("subjectHash", subjectHash)
                .param("action", action.name())
                .param("since", since.toEpochMilli())
                .query(RoomAbuseAttemptRepository::mapWindow)
                .single();

        if (attempts.count() >= limit) {
            return Optional.of(attempts.oldest().plus(window));
        }

        jdbc.sql("""
                        insert into room_abuse_attempts (
                            id, subject_hash, action, attempted_at_ms
                        ) values (:id, :subjectHash, :action, :attemptedAt)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("subjectHash", subjectHash)
                .param("action", action.name())
                .param("attemptedAt", now.toEpochMilli())
                .update();
        return Optional.empty();
    }

    public void deleteOlderThan(Instant cutoff) {
        jdbc.sql("delete from room_abuse_attempts where attempted_at_ms < :cutoff")
                .param("cutoff", cutoff.toEpochMilli())
                .update();
    }

    private static AttemptWindow mapWindow(java.sql.ResultSet resultSet, int row) throws SQLException {
        long count = resultSet.getLong("attempt_count");
        long oldest = resultSet.getLong("oldest_attempt_ms");
        return new AttemptWindow(count, count == 0 ? Instant.EPOCH : Instant.ofEpochMilli(oldest));
    }

    private record AttemptWindow(long count, Instant oldest) {
    }
}
