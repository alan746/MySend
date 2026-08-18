package com.mysend.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class EmailVerificationRepository {

    private final JdbcClient jdbc;

    public EmailVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(EmailVerification verification) {
        jdbc.sql("""
                        insert into email_verifications (
                            id, email, password_hash, code_hash, expires_at_ms,
                            consumed_at_ms, created_at_ms
                        ) values (
                            :id, :email, :passwordHash, :codeHash, :expiresAt,
                            null, :createdAt
                        )
                        """)
                .param("id", verification.id())
                .param("email", verification.email())
                .param("passwordHash", verification.passwordHash())
                .param("codeHash", verification.codeHash())
                .param("expiresAt", verification.expiresAt().toEpochMilli())
                .param("createdAt", verification.createdAt().toEpochMilli())
                .update();
    }

    public Optional<EmailVerification> findLatest(String email) {
        return jdbc.sql("""
                        select * from email_verifications
                        where email = :email
                        order by created_at_ms desc
                        limit 1
                        """)
                .param("email", email)
                .query(EmailVerificationRepository::mapVerification)
                .optional();
    }

    public boolean consume(String id, Instant now) {
        return jdbc.sql("""
                        update email_verifications
                        set consumed_at_ms = :now
                        where id = :id and consumed_at_ms is null and expires_at_ms > :now
                        """)
                .param("id", id)
                .param("now", now.toEpochMilli())
                .update() == 1;
    }

    public void deleteExpired(Instant now) {
        jdbc.sql("delete from email_verifications where expires_at_ms <= :now")
                .param("now", now.toEpochMilli())
                .update();
    }

    private static EmailVerification mapVerification(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Long consumedAt = resultSet.getObject("consumed_at_ms", Long.class);
        return new EmailVerification(
                resultSet.getString("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("code_hash"),
                Instant.ofEpochMilli(resultSet.getLong("expires_at_ms")),
                consumedAt == null ? null : Instant.ofEpochMilli(consumedAt),
                Instant.ofEpochMilli(resultSet.getLong("created_at_ms"))
        );
    }
}
