package com.mysend.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.security.DeviceIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class RoomAbuseFilter extends OncePerRequestFilter {

    private final RoomAbuseService abuse;
    private final DeviceIdentityService identities;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RoomAbuseFilter(
            RoomAbuseService abuse,
            DeviceIdentityService identities,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.abuse = abuse;
        this.identities = identities;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return action(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RoomAbuseAction action = action(request);
        if (action == null) {
            filterChain.doFilter(request, response);
            return;
        }
        var actor = identities.resolve(request, response);
        var limited = abuse.acquire(action, request, actor);
        if (limited.isPresent()) {
            reject(response, limited.get());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, RoomAbuseService.RateLimit limit)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(limit.retryAfterSeconds()));
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", limit.code(),
                "message", limit.message(),
                "fields", Map.of(
                        "retryAfterSeconds", Long.toString(limit.retryAfterSeconds())
                ),
                "timestamp", Instant.now(clock)
        ));
    }

    private static RoomAbuseAction action(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if ("/api/rooms".equals(path)) {
            return RoomAbuseAction.CREATE;
        }
        if ("/api/rooms/enter".equals(path)) {
            return RoomAbuseAction.ENTER;
        }
        if (path.matches("/api/rooms/[^/]+/files")) {
            return RoomAbuseAction.UPLOAD;
        }
        return null;
    }
}
