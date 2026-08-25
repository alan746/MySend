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

    @Test
    void passwordVerificationIsSingleUseAndExpiresAtTenMinutes() {
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        PasswordVerification verification = new PasswordVerification(
                "id",
                "account-id",
                "person@example.com",
                PasswordVerificationPurpose.RESET,
                "hash",
                now.plusSeconds(600),
                null,
                now
        );

        assertThat(verification.canUseAt(now.plusSeconds(599))).isTrue();
        assertThat(verification.canUseAt(now.plusSeconds(600))).isFalse();
    }
}
