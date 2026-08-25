package com.mysend.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class PasswordVerificationRepository {

    private final JdbcClient jdbc;

    public PasswordVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(PasswordVerification verification) {
        jdbc.sql("""
                        insert into password_verifications (
                            id, account_id, email, purpose, code_hash,
                            expires_at_ms, consumed_at_ms, created_at_ms
                        ) values (
                            :id, :accountId, :email, :purpose, :codeHash,
                            :expiresAt, null, :createdAt
                        )
                        """)
                .param("id", verification.id())
                .param("accountId", verification.accountId())
                .param("email", verification.email())
                .param("purpose", verification.purpose().name())
                .param("codeHash", verification.codeHash())
                .param("expiresAt", verification.expiresAt().toEpochMilli())
                .param("createdAt", verification.createdAt().toEpochMilli())
                .update();
    }

    public Optional<PasswordVerification> findLatest(
            String email,
            PasswordVerificationPurpose purpose
    ) {
        return jdbc.sql("""
                        select * from password_verifications
                        where email = :email and purpose = :purpose
                        order by created_at_ms desc
                        limit 1
                        """)
                .param("email", email)
                .param("purpose", purpose.name())
                .query(PasswordVerificationRepository::mapVerification)
                .optional();
    }

    public boolean consume(String id, Instant now) {
        return jdbc.sql("""
                        update password_verifications
                        set consumed_at_ms = :now
                        where id = :id and consumed_at_ms is null and expires_at_ms > :now
                        """)
                .param("id", id)
                .param("now", now.toEpochMilli())
                .update() == 1;
    }

    public void deleteExpired(Instant now) {
        jdbc.sql("delete from password_verifications where expires_at_ms <= :now")
                .param("now", now.toEpochMilli())
                .update();
    }

    private static PasswordVerification mapVerification(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Long consumedAt = resultSet.getObject("consumed_at_ms", Long.class);
        return new PasswordVerification(
                resultSet.getString("id"),
                resultSet.getString("account_id"),
                resultSet.getString("email"),
                PasswordVerificationPurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getString("code_hash"),
                Instant.ofEpochMilli(resultSet.getLong("expires_at_ms")),
                consumedAt == null ? null : Instant.ofEpochMilli(consumedAt),
                Instant.ofEpochMilli(resultSet.getLong("created_at_ms"))
        );
    }
}
