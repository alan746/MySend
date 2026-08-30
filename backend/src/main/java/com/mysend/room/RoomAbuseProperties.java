package com.mysend.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("mysend.room-abuse")
public record RoomAbuseProperties(
        @NotBlank @Size(min = 32) String hashKey,
        @Valid @NotNull Policy create,
        @Valid @NotNull Policy enter,
        @Valid @NotNull Policy upload
) {
    public Duration maximumWindow() {
        return max(create.window(), max(enter.window(), upload.window()));
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    public record Policy(
            @NotNull Duration window,
            @Min(1) int ipLimit,
            @Min(1) int actorLimit
    ) {
        public Policy {
            if (window != null && (window.isZero() || window.isNegative())) {
                throw new IllegalArgumentException("Room abuse window must be positive");
            }
        }
    }
}
