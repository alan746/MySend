package com.mysend.room;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RoomAbusePropertiesTest {

    @Test
    void rejectsNonPositiveWindows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RoomAbuseProperties.Policy(Duration.ZERO, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RoomAbuseProperties.Policy(Duration.ofSeconds(-1), 1, 1));
    }
}
