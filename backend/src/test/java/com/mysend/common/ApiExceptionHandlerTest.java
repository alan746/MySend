package com.mysend.common;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void recordsHandledApiProblemsByCodeAndStatus() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiExceptionHandler handler = new ApiExceptionHandler(registry);

        handler.handleApiException(new ApiException(
                HttpStatus.CONFLICT,
                "ROOM_CONFLICT",
                "Room changed"
        ));

        assertThat(registry.get("mysend.api.problems")
                .tag("code", "ROOM_CONFLICT")
                .tag("status", "409")
                .counter()
                .count()).isEqualTo(1);
    }
}
