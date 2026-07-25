package com.mysend.room;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class RoomAccessTokenRepository {

    private final JdbcClient jdbc;

    public RoomAccessTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            String id,
            String roomId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbc.sql("""
                        insert into room_access_tokens (
                            id, room_id, token_hash, expires_at_ms, created_at_ms
                        ) values (:id, :roomId, :tokenHash, :expiresAt, :createdAt)
                        """)
                .param("id", id)
                .param("roomId", roomId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt.toEpochMilli())
                .param("createdAt", createdAt.toEpochMilli())
                .update();
    }

    public boolean isValid(String roomId, String tokenHash, Instant now) {
        return jdbc.sql("""
                        select count(*) from room_access_tokens
                        where room_id = :roomId
                          and token_hash = :tokenHash
                          and expires_at_ms > :now
                        """)
                .param("roomId", roomId)
                .param("tokenHash", tokenHash)
                .param("now", now.toEpochMilli())
                .query(Integer.class)
                .single() > 0;
    }

    public void deleteExpired(Instant now) {
        jdbc.sql("delete from room_access_tokens where expires_at_ms <= :now")
                .param("now", now.toEpochMilli())
                .update();
    }
}
