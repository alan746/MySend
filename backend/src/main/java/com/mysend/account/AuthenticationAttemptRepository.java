package com.mysend.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthenticationAttemptRepository {

    private final JdbcClient jdbc;

    public AuthenticationAttemptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Instant> findLatest(String email, AuthenticationAttemptType type) {
        return jdbc.sql("""
                        select attempted_at_ms from authentication_attempts
                        where email = :email and attempt_type = :type
                        order by attempted_at_ms desc
                        limit 1
                        """)
                .param("email", email)
                .param("type", type.name())
                .query(Long.class)
                .optional()
                .map(Instant::ofEpochMilli);
    }

    public long countSince(
            String email,
            AuthenticationAttemptType type,
            Instant since
    ) {
        return jdbc.sql("""
                        select count(*) from authentication_attempts
                        where email = :email
                          and attempt_type = :type
                          and attempted_at_ms >= :since
                        """)
                .param("email", email)
                .param("type", type.name())
                .param("since", since.toEpochMilli())
                .query(Long.class)
                .single();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, AuthenticationAttemptType type, Instant attemptedAt) {
        jdbc.sql("""
                        insert into authentication_attempts (
                            id, email, attempt_type, attempted_at_ms
                        ) values (:id, :email, :type, :attemptedAt)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("email", email)
                .param("type", type.name())
                .param("attemptedAt", attemptedAt.toEpochMilli())
                .update();
    }

    public void clear(String email, AuthenticationAttemptType type) {
        jdbc.sql("""
                        delete from authentication_attempts
                        where email = :email and attempt_type = :type
                        """)
                .param("email", email)
                .param("type", type.name())
                .update();
    }

    public void deleteOlderThan(Instant cutoff) {
        jdbc.sql("delete from authentication_attempts where attempted_at_ms < :cutoff")
                .param("cutoff", cutoff.toEpochMilli())
                .update();
    }
}
