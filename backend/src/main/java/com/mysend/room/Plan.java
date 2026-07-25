package com.mysend.room;

public enum Plan {
    GUEST(new Limits(1, 15, 2_000, 268_435_456L, 52_428_800L, 20)),
    FREE(new Limits(2, 60, 10_000, 1_073_741_824L, 262_144_000L, 100)),
    PREMIUM(new Limits(5, 180, 100_000, 5_368_709_120L, 1_073_741_824L, 1_000));

    private final Limits limits;

    Plan(Limits limits) {
        this.limits = limits;
    }

    public Limits limits() {
        return limits;
    }

    public record Limits(
            int activeRooms,
            int maximumLifetimeMinutes,
            int clipboardCharacters,
            long roomFileBytes,
            long singleFileBytes,
            int maximumEntries
    ) {
    }
}
