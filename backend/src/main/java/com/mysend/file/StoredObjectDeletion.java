package com.mysend.file;

import java.time.Instant;

public record StoredObjectDeletion(
        String storageKey,
        long sizeBytes,
        Instant queuedAt,
        int attempts,
        Instant nextAttemptAt,
        String lastError
) {
}
