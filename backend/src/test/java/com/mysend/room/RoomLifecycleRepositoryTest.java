package com.mysend.room;

import com.mysend.account.Account;
import com.mysend.account.AccountRepository;
import com.mysend.file.RoomFile;
import com.mysend.file.RoomFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roomlifecycle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/test-lifecycle-uploads"
})
class RoomLifecycleRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomFileRepository files;

    @Autowired
    private RoomAccessTokenRepository accessTokens;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearRooms() {
        jdbc.sql("delete from rooms").update();
        jdbc.sql("delete from accounts").update();
    }

    @Test
    void claimsOnlyMatchingOpenGuestRoomsAndKeepsTheirSnapshot() {
        Instant originalExpiry = NOW.plus(Duration.ofHours(2));
        accounts.insert(new Account(
                "account-1", "owner@example.com", "password-hash", Plan.FREE,
                null, null, NOW, NOW
        ));
        rooms.insert(room("open", "1000A", "device:mine", Plan.GUEST,
                originalExpiry, null, 0, 3));
        rooms.insert(room("other-device", "1001A", "device:other", Plan.GUEST,
                originalExpiry, null, 0, 3));
        rooms.insert(room("expired", "1002A", "device:mine", Plan.GUEST,
                NOW, null, 0, 3));
        rooms.insert(room("closed", "1003A", "device:mine", Plan.GUEST,
                originalExpiry, NOW.minusSeconds(1), 0, 3));
        rooms.insert(room("exhausted", "1004A", "device:mine", Plan.GUEST,
                originalExpiry, null, 3, 3));
        rooms.insert(room("non-guest", "1005A", "device:mine", Plan.FREE,
                originalExpiry, null, 0, 3));

        assertThat(rooms.claimActiveGuestRooms("device:mine", "account-1", NOW))
                .isEqualTo(1);

        Room claimed = rooms.findById("open").orElseThrow();
        assertThat(claimed.ownerKey()).isEqualTo("account:account-1");
        assertThat(claimed.ownerAccountId()).isEqualTo("account-1");
        assertThat(claimed.plan()).isEqualTo(Plan.GUEST);
        assertThat(claimed.expiresAt()).isEqualTo(originalExpiry);
        assertThat(rooms.findById("other-device").orElseThrow().ownerKey())
                .isEqualTo("device:other");
        assertThat(rooms.claimActiveGuestRooms("device:mine", "account-1", NOW))
                .isZero();
    }

    @Test
    void finalEntryRecordsClosureAndPurgeReleasesCascadeAndCode() {
        Room exhausted = room("exhaust-on-entry", "2000B", "device:mine", Plan.GUEST,
                NOW.plus(Duration.ofHours(48)), null, 0, 1);
        rooms.insert(exhausted);
        files.insert(new RoomFile(
                "file-1", exhausted.id(), "stored-key", "name.txt",
                "text/plain", 10, NOW.minusSeconds(1)
        ));
        accessTokens.insert(
                "token-1", exhausted.id(), "token-hash",
                NOW.plusSeconds(300), NOW.minusSeconds(1)
        );

        assertThat(rooms.consumeEntry(exhausted.id(), NOW)).isTrue();
        assertThat(rooms.findById(exhausted.id()).orElseThrow().closedAt()).isEqualTo(NOW);
        assertThat(rooms.deleteByIdIfClosedBefore(exhausted.id(), NOW)).isTrue();

        assertThat(rooms.isCodeUnavailable(exhausted.accessCode())).isFalse();
        assertThat(jdbc.sql("select count(*) from room_files where room_id = :id")
                .param("id", exhausted.id()).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from room_access_tokens where room_id = :id")
                .param("id", exhausted.id()).query(Integer.class).single()).isZero();
    }

    @Test
    void manualAndExpiryClosureAreEligibleAtTheExactCleanupBoundary() {
        Instant boundary = NOW.minus(RoomCleanupJob.PURGE_ELIGIBILITY_AGE);
        rooms.insert(room("manual", "3000C", "device:mine", Plan.GUEST,
                NOW.plus(Duration.ofHours(1)), boundary, 0, 3));
        rooms.insert(room("expired", "3001C", "device:mine", Plan.GUEST,
                boundary, null, 0, 3));

        Set<String> eligibleIds = rooms.findClosedBefore(boundary).stream()
                .map(Room::id)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(eligibleIds).contains("manual", "expired");
    }

    private Room room(
            String id,
            String code,
            String ownerKey,
            Plan plan,
            Instant expiresAt,
            Instant closedAt,
            int accessCount,
            int accessLimit
    ) {
        return new Room(
                id, code, ownerKey, null, plan, RoomVisibility.PUBLIC,
                null, accessLimit, accessCount, "clipboard", 0,
                expiresAt.minus(Duration.ofMinutes(10)), expiresAt, closedAt, 0
        );
    }
}
