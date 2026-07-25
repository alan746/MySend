package com.mysend.room;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    void closesAtExpiryOrWhenEntriesAreExhausted() {
        Room active = room(NOW.plusSeconds(60), 3, 2, null);
        Room expired = room(NOW, 3, 2, null);
        Room exhausted = room(NOW.plusSeconds(60), 3, 3, null);

        assertThat(active.isClosedAt(NOW)).isFalse();
        assertThat(expired.isClosedAt(NOW)).isTrue();
        assertThat(exhausted.isClosedAt(NOW)).isTrue();
    }

    @Test
    void neverReportsNegativeRemainingEntries() {
        assertThat(room(NOW.plusSeconds(60), 2, 5, null).remainingEntries()).isZero();
    }

    private static Room room(
            Instant expiresAt,
            int limit,
            int count,
            Instant closedAt
    ) {
        return new Room(
                "room-id",
                "4821K",
                "owner",
                null,
                Plan.FREE,
                RoomVisibility.PUBLIC,
                null,
                limit,
                count,
                "",
                0,
                NOW,
                expiresAt,
                closedAt,
                0
        );
    }
}
