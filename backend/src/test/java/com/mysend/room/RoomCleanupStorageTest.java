package com.mysend.room;

import com.mysend.account.*;
import com.mysend.file.FileStore;
import com.mysend.file.RoomFile;
import com.mysend.file.RoomFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cleanupstorage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/cleanup-storage-uploads"
})
class RoomCleanupStorageTest {
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");

    @Autowired private ApplicationContext context;
    @Autowired private RoomRepository rooms;
    @Autowired private RoomFileRepository files;
    @Autowired private FileStore store;
    @Autowired private JdbcClient jdbc;

    @BeforeEach
    void clearRecords() {
        jdbc.sql("delete from rooms").update();
    }

    @Test
    void deletesNewlyExpiredClosedAndExhaustedRoomsButKeepsActiveFiles() throws Exception {
        Room expired = insert("expired", "1000A", NOW, null, 2);
        Room manual = insert("manual", "1001A", NOW.plusSeconds(600), NOW, 2);
        Room exhausted = insert("exhausted", "1002A", NOW.plusSeconds(600), null, 1);
        Room active = insert("active", "1003A", NOW.plusSeconds(1), null, 2);
        assertThat(rooms.consumeEntry(exhausted.id(), NOW)).isTrue();

        job(store).cleanExpiredRecords();

        for (Room room : new Room[] {expired, manual, exhausted}) {
            assertThat(Files.exists(store.resolve(room.id() + ".txt"))).isFalse();
            assertThat(rooms.findById(room.id())).isEmpty();
            assertThat(files.findByRoomId(room.id())).isEmpty();
            assertThat(rooms.isCodeUnavailable(room.accessCode())).isFalse();
        }
        assertThat(Files.exists(store.resolve(active.id() + ".txt"))).isTrue();
        assertThat(rooms.findById(active.id())).isPresent();
        store.delete(active.id() + ".txt");
    }

    @Test
    void continuesPastFailedDeletionAcrossBatchesAndRetriesNextCycle() throws Exception {
        for (int i = 0; i < 105; i++) {
            insert("batch-%03d".formatted(i), "%04dB".formatted(i), NOW, null, 2);
        }
        var firstBatch = rooms.findClosedBefore(NOW, "");
        assertThat(firstBatch).hasSize(100);
        assertThat(rooms.findClosedBefore(NOW, firstBatch.getLast().id())).hasSize(5);
        store.put("partial.txt", new ByteArrayInputStream(new byte[] {1}));
        files.insert(new RoomFile("partial", "batch-000", "partial.txt", "partial.txt",
                "text/plain", 1, NOW.minusSeconds(5)));
        var failingStore = new FileStore() {
            @Override public void put(String key, InputStream input) throws IOException {
                store.put(key, input);
            }
            @Override public Path resolve(String key) { return store.resolve(key); }
            @Override public void delete(String key) throws IOException {
                if (key.equals("batch-000.txt")) {
                    throw new IOException("Storage temporarily unavailable");
                }
                store.delete(key);
            }
        };

        job(failingStore).cleanExpiredRecords();

        assertThat(jdbc.sql("select count(*) from rooms").query(Integer.class).single()).isEqualTo(1);
        assertThat(Files.exists(store.resolve("batch-000.txt"))).isTrue();
        assertThat(files.findByRoomId("batch-000")).hasSize(2);
        assertThat(Files.exists(store.resolve("partial.txt"))).isFalse();
        assertThat(rooms.isCodeUnavailable("0000B")).isTrue();
        assertThat(Files.exists(store.resolve("batch-104.txt"))).isFalse();

        job(store).cleanExpiredRecords();

        assertThat(jdbc.sql("select count(*) from rooms").query(Integer.class).single()).isZero();
        assertThat(Files.exists(store.resolve("batch-000.txt"))).isFalse();
        assertThat(rooms.isCodeUnavailable("0000B")).isFalse();
    }

    private Room insert(String id, String code, Instant expiry, Instant closed, int limit) throws IOException {
        Room room = new Room(id, code, "device:test", null, Plan.FREE, RoomVisibility.PUBLIC,
                null, limit, 0, "text", 4, NOW.minusSeconds(600), expiry, closed, 0);
        rooms.insert(room);
        store.put(id + ".txt", new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        files.insert(new RoomFile(id, id, id + ".txt", "file.txt", "text/plain", 4, NOW.minusSeconds(10)));
        return room;
    }

    private RoomCleanupJob job(FileStore fileStore) {
        return new RoomCleanupJob(rooms, files, fileStore,
                context.getBean(RoomAccessTokenRepository.class), context.getBean(AppSessionRepository.class),
                context.getBean(EmailVerificationRepository.class), context.getBean(PasswordVerificationRepository.class),
                context.getBean(AuthenticationAttemptRepository.class), context.getBean(RoomAbuseAttemptRepository.class),
                context.getBean(RoomAbuseService.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
