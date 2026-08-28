package com.mysend.room;

import com.mysend.account.AppSessionRepository;
import com.mysend.account.AuthenticationAttemptRepository;
import com.mysend.account.EmailVerificationRepository;
import com.mysend.account.PasswordVerificationRepository;
import com.mysend.file.FileStore;
import com.mysend.file.RoomFile;
import com.mysend.file.RoomFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private RoomRepository rooms;
    private RoomFileRepository files;
    private FileStore fileStore;
    private RoomAccessTokenRepository accessTokens;
    private AppSessionRepository sessions;
    private EmailVerificationRepository verifications;
    private PasswordVerificationRepository passwordVerifications;
    private AuthenticationAttemptRepository authenticationAttempts;
    private RoomCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        files = mock(RoomFileRepository.class);
        fileStore = mock(FileStore.class);
        accessTokens = mock(RoomAccessTokenRepository.class);
        sessions = mock(AppSessionRepository.class);
        verifications = mock(EmailVerificationRepository.class);
        passwordVerifications = mock(PasswordVerificationRepository.class);
        authenticationAttempts = mock(AuthenticationAttemptRepository.class);
        cleanupJob = new RoomCleanupJob(
                rooms,
                files,
                fileStore,
                accessTokens,
                sessions,
                verifications,
                passwordVerifications,
                authenticationAttempts,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void removesExpiredTokensSessionsVerificationsAndRoomFiles() throws IOException {
        Instant cutoff = NOW.minus(RoomCleanupJob.ROOM_CONTENT_RETENTION);
        Room room = expiredRoom("room-1");
        RoomFile file = new RoomFile(
                "file-1",
                room.id(),
                "stored-file.pdf",
                "brief.pdf",
                "application/pdf",
                2048,
                cutoff.minusSeconds(60)
        );
        when(rooms.findClosedBefore(cutoff)).thenReturn(List.of(room));
        when(files.findByRoomId(room.id())).thenReturn(List.of(file));

        cleanupJob.cleanExpiredRecords();

        verify(accessTokens).deleteExpired(NOW);
        verify(sessions).deleteExpired(NOW);
        verify(verifications).deleteExpired(NOW);
        verify(passwordVerifications).deleteExpired(NOW);
        verify(authenticationAttempts).deleteOlderThan(NOW.minus(Duration.ofDays(1)));
        verify(fileStore).delete(file.storageKey());
        verify(rooms).deleteByIdIfClosedBefore(room.id(), cutoff);
    }

    @Test
    void keepsRoomRecordWhenStoredFileCannotBeRemoved() throws IOException {
        Instant cutoff = NOW.minus(RoomCleanupJob.ROOM_CONTENT_RETENTION);
        Room room = expiredRoom("room-2");
        RoomFile file = new RoomFile(
                "file-2",
                room.id(),
                "locked-file.zip",
                "archive.zip",
                "application/zip",
                1024,
                cutoff.minusSeconds(60)
        );
        when(rooms.findClosedBefore(cutoff)).thenReturn(List.of(room));
        when(files.findByRoomId(room.id())).thenReturn(List.of(file));
        doThrow(new IOException("file is locked"))
                .when(fileStore)
                .delete(file.storageKey());

        cleanupJob.cleanExpiredRecords();

        verify(rooms, never()).deleteByIdIfClosedBefore(room.id(), cutoff);
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
