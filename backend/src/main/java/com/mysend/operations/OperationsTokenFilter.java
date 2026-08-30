package com.mysend.operations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OperationsTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final OperationsProperties properties;

    public OperationsTokenFilter(OperationsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getRequestURI().equals("/actuator/prometheus")
                || request.getRequestURI().equals("/actuator/operations"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String supplied = authorization != null && authorization.startsWith(BEARER)
                ? authorization.substring(BEARER.length())
                : "";
        if (!secureEquals(properties.metricsToken(), supplied)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean secureEquals(String expected, String supplied) {
        byte[] expectedBytes = value(expected).getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = value(supplied).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, suppliedBytes)
                && expectedBytes.length > 0;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
