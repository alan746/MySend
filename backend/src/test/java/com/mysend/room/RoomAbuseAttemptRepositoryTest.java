package com.mysend.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roomabuseattempts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/room-abuse-attempt-uploads"
})
class RoomAbuseAttemptRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Autowired
    private RoomAbuseAttemptRepository attempts;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearAttempts() {
        jdbc.sql("delete from room_abuse_attempts").update();
    }

    @Test
    void blocksAtTheLimitAndReturnsTheRetryBoundary() {
        Duration window = Duration.ofMinutes(10);

        assertThat(attempts.acquire("subject", RoomAbuseAction.ENTER, window, 2, NOW))
                .isEmpty();
        assertThat(attempts.acquire("subject", RoomAbuseAction.ENTER, window, 2, NOW))
                .isEmpty();
        assertThat(attempts.acquire("subject", RoomAbuseAction.ENTER, window, 2, NOW))
                .contains(NOW.plus(window));
        assertThat(attemptCount()).isEqualTo(2);

        assertThat(attempts.acquire(
                "subject",
                RoomAbuseAction.ENTER,
                window,
                2,
                NOW.plus(window).plusMillis(1)
        )).isEmpty();
    }

    @Test
    void concurrentCallersCannotExceedTheLimit() throws Exception {
        int callers = 12;
        int limit = 3;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<Optional<Instant>>> work = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                work.add(() -> {
                    ready.countDown();
                    start.await();
                    return attempts.acquire(
                            "shared-subject",
                            RoomAbuseAction.UPLOAD,
                            Duration.ofMinutes(10),
                            limit,
                            NOW
                    );
                });
            }
            var futures = work.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();

            long allowed = 0;
            for (var future : futures) {
                if (future.get().isEmpty()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(limit);
            assertThat(attemptCount()).isEqualTo(limit);
        } finally {
            executor.shutdownNow();
        }
    }

    private long attemptCount() {
        return jdbc.sql("select count(*) from room_abuse_attempts")
                .query(Long.class)
                .single();
    }
}
