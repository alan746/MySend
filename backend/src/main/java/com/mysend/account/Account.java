package com.mysend.account;

import com.mysend.room.Plan;

import java.time.Instant;

public record Account(
        String id,
        String email,
        String passwordHash,
        Plan plan,
        String stripeCustomerId,
        String stripeSubscriptionId,
        Instant createdAt,
        Instant updatedAt
) {
}
