package com.mysend.billing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class StripeEventRepository {

    private final JdbcClient jdbc;

    public StripeEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(
            String eventId,
            String eventType,
            Instant eventCreatedAt,
            Instant processedAt
    ) {
        return jdbc.sql("""
                        insert into stripe_events (
                            event_id, event_type, event_created_at_ms, processed_at_ms
                        ) values (:eventId, :eventType, :eventCreatedAt, :processedAt)
                        on conflict (event_id) do nothing
                        """)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("eventCreatedAt", eventCreatedAt.toEpochMilli())
                .param("processedAt", processedAt.toEpochMilli())
                .update() == 1;
    }
}
