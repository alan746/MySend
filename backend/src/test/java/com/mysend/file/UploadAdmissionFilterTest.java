package com.mysend.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.common.ApiException;
import com.mysend.room.Plan;
import com.mysend.room.RoomAccessCookie;
import com.mysend.room.RoomService;
import com.mysend.security.DeviceIdentityService;
import com.mysend.security.OwnerIdentity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UploadAdmissionFilterTest {
    private final RoomService rooms = mock(RoomService.class);
    private final DeviceIdentityService identities = mock(DeviceIdentityService.class);
    private final OwnerIdentity owner = new OwnerIdentity("device:test", null, Plan.GUEST);
    private final UploadAdmissionFilter filter = new UploadAdmissionFilter(
            rooms, identities, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC(), 1);

    private MockHttpServletRequest upload() {
        when(identities.resolve(any(), any())).thenReturn(owner);
        return new MockHttpServletRequest("POST", "/api/rooms/1234a/files");
    }

    @Test
    void rejectsUnauthorizedRequestsWithoutReadingTheBody() throws Exception {
        var request = spy(upload());
        var response = new MockHttpServletResponse();
        when(rooms.getAuthorized("1234a", owner, null)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "ROOM_UNAVAILABLE", "Room unavailable"));
        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Rejected uploads must not reach multipart parsing");
        });
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("ROOM_UNAVAILABLE");
        verify(request, never()).getInputStream();
        verify(request, never()).getParts();
        verify(request, never()).getParameterMap();
    }

    @Test
    void forwardsScopedCookieAndAllowsAuthorizedRequests() throws Exception {
        var request = upload();
        request.setCookies(new Cookie(RoomAccessCookie.name("1234A"), "room-token"));
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        verify(rooms).getAuthorized("1234a", owner, "room-token");
        assertThat(reached).isTrue();
    }

    @Test
    void rejectsExcessConcurrentUploadsAndReleasesCapacity() throws Exception {
        var first = upload();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> {
                filter.doFilter(first, new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("Upload release timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                });
                return null;
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var rejected = new MockHttpServletResponse();
                filter.doFilter(upload(), rejected, (req, res) -> {
                    throw new AssertionError("No capacity available");
                });
                assertThat(rejected.getStatus()).isEqualTo(429);
                assertThat(rejected.getHeader("Retry-After")).isEqualTo("5");
                assertThat(rejected.getContentAsString()).contains("UPLOAD_BUSY");
            } finally {
                release.countDown();
            }
            running.get(5, TimeUnit.SECONDS);
        }
        var reached = new AtomicBoolean();
        filter.doFilter(upload(), new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }

    @Test
    void releasesCapacityWhenDownstreamFails() throws Exception {
        assertThatThrownBy(() -> filter.doFilter(upload(), new MockHttpServletResponse(), (req, res) -> {
            throw new IOException("Connection closed");
        })).isInstanceOf(IOException.class);
        var reached = new AtomicBoolean();
        filter.doFilter(upload(), new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }

    @Test
    void ignoresDownloadsAndOtherRoutes() throws Exception {
        for (var request : new MockHttpServletRequest[] {
                new MockHttpServletRequest("GET", "/api/rooms/1234A/files"),
                new MockHttpServletRequest("POST", "/api/billing/webhook")
        }) {
            var reached = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
            assertThat(reached).isTrue();
        }
        verifyNoInteractions(rooms, identities);
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> new UploadAdmissionFilter(
                rooms, identities, new ObjectMapper(), Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
