package com.mysend.account;

import java.time.Instant;

public record EmailVerification(
        String id,
        String email,
        String passwordHash,
        String codeHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {
    public boolean canUseAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
