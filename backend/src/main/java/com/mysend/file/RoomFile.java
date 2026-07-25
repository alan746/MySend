package com.mysend.file;

import java.time.Instant;

public record RoomFile(
        String id,
        String roomId,
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
