package com.mysend.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stripeevents;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
@Import(StripeEventRepository.class)
class StripeEventRepositoryTest {

    @Autowired
    private StripeEventRepository events;

    @Test
    void claimsAnEventOnlyOnce() {
        Instant createdAt = Instant.parse("2026-08-18T12:00:00Z");
        Instant processedAt = createdAt.plusSeconds(2);

        boolean first = events.claim(
                "evt_123",
                "customer.subscription.updated",
                createdAt,
                processedAt
        );
        boolean duplicate = events.claim(
                "evt_123",
                "customer.subscription.updated",
                createdAt,
                processedAt.plusSeconds(1)
        );

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
    }
}
