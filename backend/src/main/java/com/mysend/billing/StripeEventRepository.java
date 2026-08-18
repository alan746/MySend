package com.mysend.billing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;

@Repository
public class StripeEventRepository {

    private final JdbcClient jdbc;
    private final boolean postgresql;

    public StripeEventRepository(JdbcClient jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.postgresql = isPostgresql(dataSource);
    }

    public boolean claim(
            String eventId,
            String eventType,
            Instant eventCreatedAt,
            Instant processedAt
    ) {
        if (!postgresql && exists(eventId)) {
            return false;
        }
        String sql = postgresql ? """
                        insert into stripe_events (
                            event_id, event_type, event_created_at_ms, processed_at_ms
                        ) values (:eventId, :eventType, :eventCreatedAt, :processedAt)
                        on conflict (event_id) do nothing
                        """ : """
                        insert into stripe_events (
                            event_id, event_type, event_created_at_ms, processed_at_ms
                        ) values (:eventId, :eventType, :eventCreatedAt, :processedAt)
                        """;
        return jdbc.sql(sql)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("eventCreatedAt", eventCreatedAt.toEpochMilli())
                .param("processedAt", processedAt.toEpochMilli())
                .update() == 1;
    }

    private boolean exists(String eventId) {
        return jdbc.sql("select count(*) from stripe_events where event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single() > 0;
    }

    private boolean isPostgresql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not identify the billing database", exception);
        }
    }
}
