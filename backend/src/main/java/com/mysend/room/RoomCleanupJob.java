package com.mysend.room;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class RoomCleanupJob {

    private final RoomRepository rooms;
    private final RoomAccessTokenRepository accessTokens;
    private final Clock clock;

    public RoomCleanupJob(
            RoomRepository rooms,
            RoomAccessTokenRepository accessTokens,
            Clock clock
    ) {
        this.rooms = rooms;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mysend.cleanup-interval-ms:900000}")
    void cleanExpiredRecords() {
        accessTokens.deleteExpired(clock.instant());
        rooms.deleteClosedBefore(clock.instant().minus(Duration.ofDays(7)));
    }
}
