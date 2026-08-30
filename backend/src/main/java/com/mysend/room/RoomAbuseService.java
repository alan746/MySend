package com.mysend.room;

import com.mysend.security.OwnerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RoomAbuseService {

    private final RoomAbuseAttemptRepository attempts;
    private final RoomAbuseProperties properties;
    private final Clock clock;

    public RoomAbuseService(
            RoomAbuseAttemptRepository attempts,
            RoomAbuseProperties properties,
            Clock clock
    ) {
        this.attempts = attempts;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<RateLimit> acquire(
            RoomAbuseAction action,
            HttpServletRequest request,
            OwnerIdentity actor
    ) {
        RoomAbuseProperties.Policy policy = policy(action);
        Instant now = clock.instant();
        Optional<Instant> ipRetry = attempts.acquire(
                subject("ip", normalizeAddress(request.getRemoteAddr())),
                action,
                policy.window(),
                policy.ipLimit(),
                now
        );
        Optional<Instant> actorRetry = attempts.acquire(
                subject("actor", actor.ownerKey()),
                action,
                policy.window(),
                policy.actorLimit(),
                now
        );
        return later(ipRetry, actorRetry)
                .map(retryAt -> new RateLimit(
                        action,
                        secondsUntil(now, retryAt)
                ));
    }

    public Duration retention() {
        return properties.maximumWindow().plus(Duration.ofHours(1));
    }

    private RoomAbuseProperties.Policy policy(RoomAbuseAction action) {
        return switch (action) {
            case CREATE -> properties.create();
            case ENTER -> properties.enter();
            case UPLOAD -> properties.upload();
        };
    }

    private static Optional<Instant> later(
            Optional<Instant> first,
            Optional<Instant> second
    ) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(first.get().isAfter(second.get()) ? first.get() : second.get());
    }

    private String subject(String kind, String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.strip();
        return hmac(properties.hashKey(), kind + ":" + normalized);
    }

    private static String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String stripped = value.strip();
        if (!stripped.matches("[0-9a-fA-F:.]+")) {
            return stripped;
        }
        try {
            return InetAddress.getByName(stripped).getHostAddress();
        } catch (UnknownHostException exception) {
            return stripped;
        }
    }

    private static String hmac(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static long secondsUntil(Instant now, Instant retryAt) {
        long milliseconds = Math.max(1, Duration.between(now, retryAt).toMillis());
        return Math.max(1, (milliseconds + 999) / 1000);
    }

    public record RateLimit(RoomAbuseAction action, long retryAfterSeconds) {
        public String code() {
            return switch (action) {
                case CREATE -> "ROOM_CREATE_RATE_LIMITED";
                case ENTER -> "ROOM_ENTRY_RATE_LIMITED";
                case UPLOAD -> "ROOM_UPLOAD_RATE_LIMITED";
            };
        }

        public String message() {
            return switch (action) {
                case CREATE -> "Too many room creation attempts. Try again later";
                case ENTER -> "Too many room entry attempts. Try again later";
                case UPLOAD -> "Too many file upload attempts. Try again later";
            };
        }
    }
}
