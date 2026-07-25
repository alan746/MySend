package com.mysend.file;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RoomFileRepository {

    private final JdbcClient jdbc;

    public RoomFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(RoomFile file) {
        jdbc.sql("""
                        insert into room_files (
                            id, room_id, storage_key, original_name,
                            content_type, size_bytes, uploaded_at_ms
                        ) values (
                            :id, :roomId, :storageKey, :originalName,
                            :contentType, :sizeBytes, :uploadedAt
                        )
                        """)
                .param("id", file.id())
                .param("roomId", file.roomId())
                .param("storageKey", file.storageKey())
                .param("originalName", file.originalName())
                .param("contentType", file.contentType())
                .param("sizeBytes", file.sizeBytes())
                .param("uploadedAt", file.uploadedAt().toEpochMilli())
                .update();
    }

    public List<RoomFile> findByRoomId(String roomId) {
        return jdbc.sql("""
                        select * from room_files
                        where room_id = :roomId
                        order by uploaded_at_ms desc
                        """)
                .param("roomId", roomId)
                .query(RoomFileRepository::mapFile)
                .list();
    }

    public Optional<RoomFile> findByIdAndRoomId(String id, String roomId) {
        return jdbc.sql("""
                        select * from room_files
                        where id = :id and room_id = :roomId
                        """)
                .param("id", id)
                .param("roomId", roomId)
                .query(RoomFileRepository::mapFile)
                .optional();
    }

    public boolean delete(String id, String roomId) {
        return jdbc.sql("delete from room_files where id = :id and room_id = :roomId")
                .param("id", id)
                .param("roomId", roomId)
                .update() == 1;
    }

    private static RoomFile mapFile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RoomFile(
                resultSet.getString("id"),
                resultSet.getString("room_id"),
                resultSet.getString("storage_key"),
                resultSet.getString("original_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                Instant.ofEpochMilli(resultSet.getLong("uploaded_at_ms"))
        );
    }
}
