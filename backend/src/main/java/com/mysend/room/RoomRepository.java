package com.mysend.room;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RoomRepository {

    private final JdbcClient jdbc;

    public RoomRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countActiveByOwner(String ownerKey, Instant now) {
        return jdbc.sql("""
                        select count(*) from rooms
                        where owner_key = :ownerKey
                          and closed_at_ms is null
                          and expires_at_ms > :now
                          and access_count < access_limit
                        """)
                .param("ownerKey", ownerKey)
                .param("now", now.toEpochMilli())
                .query(Long.class)
                .single();
    }

    public boolean isCodeUnavailable(String code) {
        return jdbc.sql("select count(*) from rooms where access_code = :code")
                .param("code", code)
                .query(Integer.class)
                .single() > 0;
    }

    public void insert(Room room) {
        jdbc.sql("""
                        insert into rooms (
                            id, access_code, owner_key, owner_account_id, plan,
                            visibility, password_hash, access_limit, access_count,
                            clipboard_text, file_bytes, created_at_ms, expires_at_ms,
                            closed_at_ms, version
                        ) values (
                            :id, :accessCode, :ownerKey, :ownerAccountId, :plan,
                            :visibility, :passwordHash, :accessLimit, :accessCount,
                            :clipboardText, :fileBytes, :createdAt, :expiresAt,
                            :closedAt, :version
                        )
                        """)
                .param("id", room.id())
                .param("accessCode", room.accessCode())
                .param("ownerKey", room.ownerKey())
                .param("ownerAccountId", room.ownerAccountId())
                .param("plan", room.plan().name())
                .param("visibility", room.visibility().name())
                .param("passwordHash", room.passwordHash())
                .param("accessLimit", room.accessLimit())
                .param("accessCount", room.accessCount())
                .param("clipboardText", room.clipboardText())
                .param("fileBytes", room.fileBytes())
                .param("createdAt", room.createdAt().toEpochMilli())
                .param("expiresAt", room.expiresAt().toEpochMilli())
                .param("closedAt", toEpochMilli(room.closedAt()))
                .param("version", room.version())
                .update();
    }

    public Optional<Room> findByCode(String normalizedCode) {
        return jdbc.sql("select * from rooms where access_code = :code")
                .param("code", normalizedCode)
                .query(RoomRepository::mapRoom)
                .optional();
    }

    public Optional<Room> findById(String id) {
        return jdbc.sql("select * from rooms where id = :id")
                .param("id", id)
                .query(RoomRepository::mapRoom)
                .optional();
    }

    public List<Room> findActiveByOwner(String ownerKey, Instant now) {
        return jdbc.sql("""
                        select * from rooms
                        where owner_key = :ownerKey
                          and closed_at_ms is null
                          and expires_at_ms > :now
                        order by created_at_ms desc
                        """)
                .param("ownerKey", ownerKey)
                .param("now", now.toEpochMilli())
                .query(RoomRepository::mapRoom)
                .list();
    }

    public boolean consumeEntry(String roomId, Instant now) {
        return jdbc.sql("""
                        update rooms
                        set access_count = access_count + 1, version = version + 1
                        where id = :id
                          and closed_at_ms is null
                          and expires_at_ms > :now
                          and access_count < access_limit
                        """)
                .param("id", roomId)
                .param("now", now.toEpochMilli())
                .update() == 1;
    }

    public boolean updateClipboard(String id, long expectedVersion, String text) {
        return jdbc.sql("""
                        update rooms
                        set clipboard_text = :text, version = version + 1
                        where id = :id and version = :version and closed_at_ms is null
                        """)
                .param("text", text)
                .param("id", id)
                .param("version", expectedVersion)
                .update() == 1;
    }

    public boolean updateSettings(
            String id,
            long expectedVersion,
            RoomVisibility visibility,
            String passwordHash,
            int accessLimit,
            Instant expiresAt
    ) {
        return jdbc.sql("""
                        update rooms
                        set visibility = :visibility,
                            password_hash = :passwordHash,
                            access_limit = :accessLimit,
                            expires_at_ms = :expiresAt,
                            version = version + 1
                        where id = :id and version = :version and closed_at_ms is null
                        """)
                .param("visibility", visibility.name())
                .param("passwordHash", passwordHash)
                .param("accessLimit", accessLimit)
                .param("expiresAt", expiresAt.toEpochMilli())
                .param("id", id)
                .param("version", expectedVersion)
                .update() == 1;
    }

    public boolean close(String id, String ownerKey, Instant now) {
        return jdbc.sql("""
                        update rooms
                        set closed_at_ms = :now, version = version + 1
                        where id = :id and owner_key = :ownerKey and closed_at_ms is null
                        """)
                .param("now", now.toEpochMilli())
                .param("id", id)
                .param("ownerKey", ownerKey)
                .update() == 1;
    }

    public void deleteClosedBefore(Instant cutoff) {
        jdbc.sql("""
                        delete from rooms
                        where (closed_at_ms is not null and closed_at_ms < :cutoff)
                           or expires_at_ms < :cutoff
                        """)
                .param("cutoff", cutoff.toEpochMilli())
                .update();
    }

    public boolean adjustFileBytes(String id, long delta, long maximumBytes) {
        return jdbc.sql("""
                        update rooms
                        set file_bytes = file_bytes + :delta, version = version + 1
                        where id = :id
                          and file_bytes + :delta >= 0
                          and file_bytes + :delta <= :maximum
                        """)
                .param("delta", delta)
                .param("id", id)
                .param("maximum", maximumBytes)
                .update() == 1;
    }

    private static Room mapRoom(ResultSet resultSet, int rowNumber) throws SQLException {
        Long closedAt = resultSet.getObject("closed_at_ms", Long.class);
        return new Room(
                resultSet.getString("id"),
                resultSet.getString("access_code"),
                resultSet.getString("owner_key"),
                resultSet.getString("owner_account_id"),
                Plan.valueOf(resultSet.getString("plan")),
                RoomVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getString("password_hash"),
                resultSet.getInt("access_limit"),
                resultSet.getInt("access_count"),
                resultSet.getString("clipboard_text"),
                resultSet.getLong("file_bytes"),
                Instant.ofEpochMilli(resultSet.getLong("created_at_ms")),
                Instant.ofEpochMilli(resultSet.getLong("expires_at_ms")),
                closedAt == null ? null : Instant.ofEpochMilli(closedAt),
                resultSet.getLong("version")
        );
    }

    private static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
