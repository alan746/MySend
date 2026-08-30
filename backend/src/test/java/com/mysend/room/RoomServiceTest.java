package com.mysend.room;

import com.mysend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    @Test
    void performsPasswordWorkForUnknownRoomEntries() {
        RoomRepository rooms = mock(RoomRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("unavailable-password-hash");
        RoomService service = new RoomService(
                rooms,
                mock(RoomAccessService.class),
                mock(AccessCodeGenerator.class),
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.enter("0000A", "wrong-password"))
                .isInstanceOf(ApiException.class);
        verify(passwordEncoder).matches(
                "wrong-password",
                "unavailable-password-hash"
        );
    }

    @Test
    void performsEquivalentPasswordWorkForPublicRoomEntries() {
        Instant now = Instant.parse("2026-08-29T12:00:00Z");
        RoomRepository rooms = mock(RoomRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("unavailable-password-hash");
        Room room = new Room(
                "room-1",
                "0000A",
                "device:owner",
                null,
                Plan.GUEST,
                RoomVisibility.PUBLIC,
                null,
                10,
                0,
                "",
                0,
                now,
                now.plusSeconds(900),
                null,
                0
        );
        when(rooms.findByCode("0000A")).thenReturn(Optional.of(room));
        when(rooms.consumeEntry("room-1", now)).thenReturn(true);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        RoomAccessService roomAccess = mock(RoomAccessService.class);
        when(roomAccess.issue(room)).thenReturn("room-token");
        RoomService service = new RoomService(
                rooms,
                roomAccess,
                mock(AccessCodeGenerator.class),
                passwordEncoder,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        service.enter("0000A", null);

        verify(passwordEncoder).matches("", "unavailable-password-hash");
    }
}
