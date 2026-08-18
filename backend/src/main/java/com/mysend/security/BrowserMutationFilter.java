package com.mysend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class BrowserMutationFilter extends OncePerRequestFilter {

    public static final String REQUEST_MARKER = "MySendWeb";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BrowserMutationFilter(
            AppProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.allowedOrigins = properties.webOrigins().stream()
                .map(BrowserMutationFilter::normalizeOrigin)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || SAFE_METHODS.contains(request.getMethod())
                || path.equals("/api/billing/webhook");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String marker = request.getHeader("X-Requested-With");
        if (!REQUEST_MARKER.equals(marker)) {
            reject(response, "REQUEST_MARKER_REQUIRED", "This request did not come from the MySend web client");
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(normalizeOrigin(origin))) {
            reject(response, "WEB_ORIGIN_REQUIRED", "This request origin is not allowed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", code,
                "message", message,
                "fields", Map.of(),
                "timestamp", Instant.now(clock)
        ));
    }

    private static String normalizeOrigin(String origin) {
        String normalized = origin == null ? "" : origin.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
