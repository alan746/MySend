package com.mysend.account;

import java.time.Instant;

public record PasswordVerification(
        String id,
        String accountId,
        String email,
        PasswordVerificationPurpose purpose,
        String codeHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {
    public boolean canUseAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
