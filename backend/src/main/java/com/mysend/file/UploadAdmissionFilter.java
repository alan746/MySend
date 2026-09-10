package com.mysend.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.common.ApiException;
import com.mysend.common.ApiExceptionHandler.ApiProblem;
import com.mysend.room.RoomAccessCookie;
import com.mysend.room.RoomService;
import com.mysend.security.DeviceIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Component
public class UploadAdmissionFilter extends OncePerRequestFilter {

    private static final Pattern UPLOAD_PATH = Pattern.compile("/api/rooms/([^/]+)/files");

    private final RoomService rooms;
    private final DeviceIdentityService identities;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Semaphore slots;

    public UploadAdmissionFilter(
            RoomService rooms,
            DeviceIdentityService identities,
            ObjectMapper mapper,
            Clock clock,
            @Value("${mysend.upload-max-concurrent:4}") int maximumConcurrent
    ) {
        Assert.isTrue(maximumConcurrent > 0, "Upload concurrency must be positive");
        this.rooms = rooms;
        this.identities = identities;
        this.mapper = mapper;
        this.clock = clock;
        this.slots = new Semaphore(maximumConcurrent);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !UPLOAD_PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        var path = UPLOAD_PATH.matcher(request.getRequestURI());
        if (!path.matches()) {
            chain.doFilter(request, response);
            return;
        }
        String code = path.group(1);
        try {
            rooms.getAuthorized(code, identities.resolve(request, response),
                    RoomAccessCookie.read(request, code).orElse(null));
        } catch (ApiException exception) {
            reject(response, exception.status().value(), exception.code(), exception.getMessage());
            return;
        }
        if (!slots.tryAcquire()) {
            response.setHeader("Retry-After", "5");
            reject(response, 429, "UPLOAD_BUSY", "Uploads are busy; please retry shortly");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            slots.release();
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ApiProblem(code, message, Map.of(), clock.instant()));
    }
}
