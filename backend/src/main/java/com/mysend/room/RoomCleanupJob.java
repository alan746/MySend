package com.mysend.room;

import com.mysend.account.AppSessionRepository;
import com.mysend.account.AuthenticationAttemptRepository;
import com.mysend.account.EmailVerificationRepository;
import com.mysend.account.PasswordVerificationRepository;
import com.mysend.file.StorageDeletionService;
import com.mysend.operations.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class RoomCleanupJob {

    static final Duration PURGE_DEADLINE = Duration.ofHours(24);
    static final Duration DEFAULT_SCHEDULE_INTERVAL = Duration.ofMinutes(15);
    static final Duration PURGE_ELIGIBILITY_AGE = PURGE_DEADLINE
            .minus(DEFAULT_SCHEDULE_INTERVAL);

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupJob.class);

    private final RoomRepository rooms;
    private final StorageDeletionService storageDeletions;
    private final RoomAccessTokenRepository accessTokens;
    private final AppSessionRepository sessions;
    private final EmailVerificationRepository verifications;
    private final PasswordVerificationRepository passwordVerifications;
    private final AuthenticationAttemptRepository authenticationAttempts;
    private final RoomAbuseAttemptRepository roomAbuseAttempts;
    private final RoomAbuseService roomAbuse;
    private final OperationalMetrics metrics;
    private final Clock clock;

    public RoomCleanupJob(
            RoomRepository rooms,
            StorageDeletionService storageDeletions,
            RoomAccessTokenRepository accessTokens,
            AppSessionRepository sessions,
            EmailVerificationRepository verifications,
            PasswordVerificationRepository passwordVerifications,
            AuthenticationAttemptRepository authenticationAttempts,
            RoomAbuseAttemptRepository roomAbuseAttempts,
            RoomAbuseService roomAbuse,
            OperationalMetrics metrics,
            Clock clock
    ) {
        this.rooms = rooms;
        this.storageDeletions = storageDeletions;
        this.accessTokens = accessTokens;
        this.sessions = sessions;
        this.verifications = verifications;
        this.passwordVerifications = passwordVerifications;
        this.authenticationAttempts = authenticationAttempts;
        this.roomAbuseAttempts = roomAbuseAttempts;
        this.roomAbuse = roomAbuse;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mysend.cleanup-interval-ms:900000}")
    void cleanExpiredRecords() {
        var sample = metrics.startCleanup();
        var now = clock.instant();
        var roomCutoff = now.minus(PURGE_ELIGIBILITY_AGE);
        try {
            accessTokens.deleteExpired(now);
            sessions.deleteExpired(now);
            verifications.deleteExpired(now);
            passwordVerifications.deleteExpired(now);
            authenticationAttempts.deleteOlderThan(now.minus(Duration.ofDays(1)));
            roomAbuseAttempts.deleteOlderThan(now.minus(roomAbuse.retention()));
            rooms.findClosedBefore(roomCutoff)
                    .forEach(room -> deleteRoom(room, roomCutoff, now));
            metrics.finishCleanup(sample);
        } catch (RuntimeException exception) {
            metrics.recordCleanupFailure();
            throw exception;
        }
    }

    private void deleteRoom(Room room, Instant cutoff, Instant now) {
        try {
            if (storageDeletions.purgeRoom(room, cutoff, now)) {
                Duration lag = Duration.between(room.logicalClosureAt(), now);
                metrics.recordRoomPurged(lag);
                log.info(
                        "Purged room {} at {} seconds of purge lag",
                        room.id(),
                        lag.toSeconds()
                );
            }
        } catch (RuntimeException exception) {
            metrics.recordCleanupFailure();
            log.warn(
                    "Could not queue stored files for room {} at {} seconds of purge lag",
                    room.id(),
                    Duration.between(room.logicalClosureAt(), now).toSeconds(),
                    exception
            );
        }
    }
}
