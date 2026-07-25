package com.mysend.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    @Test
    void guestPlanKeepsNoLoginSharingLightweight() {
        Plan.Limits limits = Plan.GUEST.limits();

        assertThat(limits.activeRooms()).isEqualTo(1);
        assertThat(limits.maximumLifetimeMinutes()).isEqualTo(15);
        assertThat(limits.clipboardCharacters()).isEqualTo(2_000);
        assertThat(limits.maximumEntries()).isEqualTo(20);
    }

    @Test
    void freePlanKeepsShortLivedServiceLimits() {
        Plan.Limits limits = Plan.FREE.limits();

        assertThat(limits.activeRooms()).isEqualTo(2);
        assertThat(limits.maximumLifetimeMinutes()).isEqualTo(60);
        assertThat(limits.clipboardCharacters()).isEqualTo(10_000);
        assertThat(limits.roomFileBytes()).isEqualTo(1_073_741_824L);
    }

    @Test
    void premiumPlanExpandsWithoutBecomingPermanentStorage() {
        Plan.Limits limits = Plan.PREMIUM.limits();

        assertThat(limits.activeRooms()).isEqualTo(5);
        assertThat(limits.maximumLifetimeMinutes()).isEqualTo(180);
        assertThat(limits.clipboardCharacters()).isEqualTo(100_000);
        assertThat(limits.roomFileBytes()).isEqualTo(5_368_709_120L);
    }
}
