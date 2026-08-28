package com.mysend.room;

import com.mysend.account.AppSessionRepository;
import com.mysend.account.AuthenticationAttemptRepository;
import com.mysend.account.EmailVerificationRepository;
import com.mysend.account.PasswordVerificationRepository;
import com.mysend.file.FileStore;
import com.mysend.file.RoomFile;
import com.mysend.file.RoomFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    private final RoomFileRepository files;
    private final FileStore fileStore;
    private final RoomAccessTokenRepository accessTokens;
    private final AppSessionRepository sessions;
    private final EmailVerificationRepository verifications;
    private final PasswordVerificationRepository passwordVerifications;
    private final AuthenticationAttemptRepository authenticationAttempts;
    private final Clock clock;

    public RoomCleanupJob(
            RoomRepository rooms,
            RoomFileRepository files,
            FileStore fileStore,
            RoomAccessTokenRepository accessTokens,
            AppSessionRepository sessions,
            EmailVerificationRepository verifications,
            PasswordVerificationRepository passwordVerifications,
            AuthenticationAttemptRepository authenticationAttempts,
            Clock clock
    ) {
        this.rooms = rooms;
        this.files = files;
        this.fileStore = fileStore;
        this.accessTokens = accessTokens;
        this.sessions = sessions;
        this.verifications = verifications;
        this.passwordVerifications = passwordVerifications;
        this.authenticationAttempts = authenticationAttempts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mysend.cleanup-interval-ms:900000}")
    void cleanExpiredRecords() {
        var now = clock.instant();
        var roomCutoff = now.minus(PURGE_ELIGIBILITY_AGE);

        accessTokens.deleteExpired(now);
        sessions.deleteExpired(now);
        verifications.deleteExpired(now);
        passwordVerifications.deleteExpired(now);
        authenticationAttempts.deleteOlderThan(now.minus(Duration.ofDays(1)));
        rooms.findClosedBefore(roomCutoff)
                .forEach(room -> deleteRoom(room, roomCutoff, now));
    }

    private void deleteRoom(Room room, Instant cutoff, Instant now) {
        try {
            for (RoomFile file : files.findByRoomId(room.id())) {
                fileStore.delete(file.storageKey());
            }
            if (rooms.deleteByIdIfClosedBefore(room.id(), cutoff)) {
                log.info(
                        "Purged room {} at {} seconds of purge lag",
                        room.id(),
                        Duration.between(room.logicalClosureAt(), now).toSeconds()
                );
            }
        } catch (IOException exception) {
            log.warn(
                    "Could not remove stored files for room {} at {} seconds of purge lag",
                    room.id(),
                    Duration.between(room.logicalClosureAt(), now).toSeconds(),
                    exception
            );
        }
    }
}
