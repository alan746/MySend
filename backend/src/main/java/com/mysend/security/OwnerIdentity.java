package com.mysend.security;

import com.mysend.room.Plan;

public record OwnerIdentity(
        String ownerKey,
        String accountId,
        Plan plan
) {
}
