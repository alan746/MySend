package com.mysend.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    @Test
    void normalizesEmailBeforeUniquenessChecks() {
        assertThat(AccountService.normalizeEmail("  Person@Example.COM "))
                .isEqualTo("person@example.com");
    }

    @Test
    void verificationIsSingleUseAndExpiresAtTenMinutes() {
        Instant now = Instant.parse("2026-07-24T12:00:00Z");
        EmailVerification verification = new EmailVerification(
                "id",
                "person@example.com",
                "password",
                "hash",
                now.plusSeconds(600),
                null,
                now
        );

        assertThat(verification.canUseAt(now.plusSeconds(599))).isTrue();
        assertThat(verification.canUseAt(now.plusSeconds(600))).isFalse();
    }
}
