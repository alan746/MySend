package com.mysend.room;

import com.mysend.account.AppSessionRepository;
import com.mysend.account.AuthenticationAttemptRepository;
import com.mysend.account.EmailVerificationRepository;
import com.mysend.account.PasswordVerificationRepository;
import com.mysend.file.StorageDeletionService;
import com.mysend.operations.OperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private RoomRepository rooms;
    private StorageDeletionService storageDeletions;
    private RoomAccessTokenRepository accessTokens;
    private AppSessionRepository sessions;
    private EmailVerificationRepository verifications;
    private PasswordVerificationRepository passwordVerifications;
    private AuthenticationAttemptRepository authenticationAttempts;
    private RoomAbuseAttemptRepository roomAbuseAttempts;
    private RoomAbuseService roomAbuse;
    private OperationalMetrics metrics;
    private RoomCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        storageDeletions = mock(StorageDeletionService.class);
        accessTokens = mock(RoomAccessTokenRepository.class);
        sessions = mock(AppSessionRepository.class);
        verifications = mock(EmailVerificationRepository.class);
        passwordVerifications = mock(PasswordVerificationRepository.class);
        authenticationAttempts = mock(AuthenticationAttemptRepository.class);
        roomAbuseAttempts = mock(RoomAbuseAttemptRepository.class);
        roomAbuse = mock(RoomAbuseService.class);
        metrics = mock(OperationalMetrics.class);
        when(roomAbuse.retention()).thenReturn(Duration.ofHours(2));
        cleanupJob = new RoomCleanupJob(
                rooms,
                storageDeletions,
                accessTokens,
                sessions,
                verifications,
                passwordVerifications,
                authenticationAttempts,
                roomAbuseAttempts,
                roomAbuse,
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void removesExpiredTokensSessionsVerificationsAndQueuesRoomFiles() {
        Instant cutoff = NOW.minus(RoomCleanupJob.PURGE_ELIGIBILITY_AGE);
        Room room = expiredRoom("room-1");
        when(rooms.findClosedBefore(cutoff)).thenReturn(List.of(room));
        when(storageDeletions.purgeRoom(room, cutoff, NOW)).thenReturn(true);

        cleanupJob.cleanExpiredRecords();

        verify(accessTokens).deleteExpired(NOW);
        verify(sessions).deleteExpired(NOW);
        verify(verifications).deleteExpired(NOW);
        verify(passwordVerifications).deleteExpired(NOW);
        verify(authenticationAttempts).deleteOlderThan(NOW.minus(Duration.ofDays(1)));
        verify(roomAbuseAttempts).deleteOlderThan(NOW.minus(Duration.ofHours(2)));
        verify(storageDeletions).purgeRoom(room, cutoff, NOW);
        verify(metrics).recordRoomPurged(Duration.ofSeconds(90000));
    }

    @Test
    void continuesCleanupWhenRoomFilesCannotBeQueued() {
        Instant cutoff = NOW.minus(RoomCleanupJob.PURGE_ELIGIBILITY_AGE);
        Room room = expiredRoom("room-2");
        when(rooms.findClosedBefore(cutoff)).thenReturn(List.of(room));
        doThrow(new IllegalStateException("database unavailable"))
                .when(storageDeletions)
                .purgeRoom(room, cutoff, NOW);

        cleanupJob.cleanExpiredRecords();

        verify(storageDeletions).purgeRoom(room, cutoff, NOW);
        verify(metrics).recordCleanupFailure();
    }

    private Room expiredRoom(String id) {
        return new Room(
                id,
                "1234A",
                "owner-key",
                null,
                Plan.GUEST,
                RoomVisibility.PUBLIC,
                null,
                5,
                0,
                "",
                0,
                NOW.minus(Duration.ofHours(26)),
                NOW.minus(Duration.ofHours(25)),
                null,
                0
        );
    }
}
