package com.mysend.room;

import java.time.Instant;

public record Room(
        String id,
        String accessCode,
        String ownerKey,
        String ownerAccountId,
        Plan plan,
        RoomVisibility visibility,
        String passwordHash,
        int accessLimit,
        int accessCount,
        String clipboardText,
        long fileBytes,
        Instant createdAt,
        Instant expiresAt,
        Instant closedAt,
        long version
) {
    public boolean isClosedAt(Instant now) {
        return closedAt != null
                || !expiresAt.isAfter(now)
                || accessCount >= accessLimit;
    }

    public Instant logicalClosureAt() {
        if (closedAt == null) {
            return expiresAt;
        }
        return closedAt.isBefore(expiresAt) ? closedAt : expiresAt;
    }

    public int remainingEntries() {
        return Math.max(0, accessLimit - accessCount);
    }

    public boolean isOwnedBy(String candidateOwnerKey) {
        return ownerKey.equals(candidateOwnerKey);
    }
}
