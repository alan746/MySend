package com.mysend.account;

import com.mysend.common.Hashing;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class AppSessionRepository {

    private final JdbcClient jdbc;

    public AppSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            String id,
            String accountId,
            String rawToken,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbc.sql("""
                        insert into app_sessions (
                            id, account_id, token_hash, expires_at_ms, created_at_ms
                        ) values (:id, :accountId, :tokenHash, :expiresAt, :createdAt)
                        """)
                .param("id", id)
                .param("accountId", accountId)
                .param("tokenHash", Hashing.sha256(rawToken))
                .param("expiresAt", expiresAt.toEpochMilli())
                .param("createdAt", createdAt.toEpochMilli())
                .update();
    }

    public Optional<String> findAccountId(String rawToken, Instant now) {
        return jdbc.sql("""
                        select account_id from app_sessions
                        where token_hash = :tokenHash and expires_at_ms > :now
                        """)
                .param("tokenHash", Hashing.sha256(rawToken))
                .param("now", now.toEpochMilli())
                .query(String.class)
                .optional();
    }

    public void delete(String rawToken) {
        jdbc.sql("delete from app_sessions where token_hash = :tokenHash")
                .param("tokenHash", Hashing.sha256(rawToken))
                .update();
    }

    public void deleteExpired(Instant now) {
        jdbc.sql("delete from app_sessions where expires_at_ms <= :now")
                .param("now", now.toEpochMilli())
                .update();
    }
}
