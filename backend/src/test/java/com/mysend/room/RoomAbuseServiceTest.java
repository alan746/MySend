package com.mysend.room;

import com.mysend.common.Hashing;
import com.mysend.security.OwnerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomAbuseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Test
    void protectsStoredSubjectsWithASecretKey() {
        RoomAbuseAttemptRepository attempts = mock(RoomAbuseAttemptRepository.class);
        when(attempts.acquire(any(), any(), any(), anyInt(), eq(NOW)))
                .thenReturn(Optional.empty());
        RoomAbuseProperties.Policy policy = new RoomAbuseProperties.Policy(
                Duration.ofMinutes(10),
                10,
                10
        );
        RoomAbuseService service = new RoomAbuseService(
                attempts,
                new RoomAbuseProperties(
                        "test-room-abuse-hash-key-1234567890",
                        policy,
                        policy,
                        policy
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");

        service.acquire(
                RoomAbuseAction.CREATE,
                request,
                new OwnerIdentity("device:owner", null, Plan.GUEST)
        );

        var hashes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(attempts, times(2)).acquire(
                hashes.capture(),
                eq(RoomAbuseAction.CREATE),
                eq(Duration.ofMinutes(10)),
                eq(10),
                eq(NOW)
        );
        assertThat(hashes.getAllValues().getFirst())
                .hasSize(64)
                .isNotEqualTo(Hashing.sha256("ip:203.0.113.10"));
    }

    @Test
    void normalizesEquivalentIpAddresses() {
        RoomAbuseAttemptRepository attempts = mock(RoomAbuseAttemptRepository.class);
        when(attempts.acquire(any(), any(), any(), anyInt(), eq(NOW)))
                .thenReturn(Optional.empty());
        RoomAbuseProperties.Policy policy = new RoomAbuseProperties.Policy(
                Duration.ofMinutes(10),
                10,
                10
        );
        RoomAbuseService service = new RoomAbuseService(
                attempts,
                new RoomAbuseProperties(
                        "test-room-abuse-hash-key-1234567890",
                        policy,
                        policy,
                        policy
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpServletRequest expanded = mock(HttpServletRequest.class);
        HttpServletRequest compressed = mock(HttpServletRequest.class);
        when(expanded.getRemoteAddr()).thenReturn("2001:db8:0:0:0:0:0:1");
        when(compressed.getRemoteAddr()).thenReturn("2001:db8::1");
        OwnerIdentity actor = new OwnerIdentity("device:owner", null, Plan.GUEST);

        service.acquire(RoomAbuseAction.ENTER, expanded, actor);
        service.acquire(RoomAbuseAction.ENTER, compressed, actor);

        var hashes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(attempts, times(4)).acquire(
                hashes.capture(),
                eq(RoomAbuseAction.ENTER),
                eq(Duration.ofMinutes(10)),
                eq(10),
                eq(NOW)
        );
        assertThat(hashes.getAllValues().get(0))
                .isEqualTo(hashes.getAllValues().get(2));
    }
}
