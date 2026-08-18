package com.mysend.account;

import com.mysend.common.ApiException;
import com.mysend.common.Hashing;
import com.mysend.room.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRateLimitTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final String EMAIL = "person@example.com";

    private AccountRepository accounts;
    private EmailVerificationRepository verifications;
    private AuthenticationAttemptRepository attempts;
    private VerificationMailer mailer;
    private AccountSessionService sessions;
    private PasswordEncoder passwordEncoder;
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        verifications = mock(EmailVerificationRepository.class);
        attempts = mock(AuthenticationAttemptRepository.class);
        mailer = mock(VerificationMailer.class);
        sessions = mock(AccountSessionService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        random = mock(SecureRandom.class);
    }

    @Test
    void appliesResendCooldownAndRecoversAfterItEnds() {
        when(attempts.findLatest(EMAIL, AuthenticationAttemptType.VERIFICATION_SEND))
                .thenReturn(Optional.of(NOW.minusSeconds(30)));
        AccountService blocked = serviceAt(NOW);

        assertRateLimit(
                () -> blocked.requestVerification(EMAIL, "long-password"),
                "VERIFICATION_COOLDOWN"
        );
        verify(mailer, never()).deliver(any(), any());

        Instant recoveredAt = NOW.plusSeconds(31);
        when(attempts.countSince(
                EMAIL,
                AuthenticationAttemptType.VERIFICATION_SEND,
                recoveredAt.minus(AccountService.VERIFICATION_SEND_WINDOW)
        )).thenReturn(1L);
        when(passwordEncoder.encode("long-password")).thenReturn("password-hash");
        when(random.nextInt(1_000_000)).thenReturn(123456);
        when(mailer.deliver(EMAIL, "123456")).thenReturn(true);

        AccountService.VerificationResult result = serviceAt(recoveredAt)
                .requestVerification(EMAIL, "long-password");

        assertThat(result.expiresAt()).isEqualTo(recoveredAt.plusSeconds(600));
        verify(attempts).record(
                EMAIL,
                AuthenticationAttemptType.VERIFICATION_SEND,
                recoveredAt
        );
    }

    @Test
    void capsVerificationEmailsWithinRollingWindow() {
        when(attempts.findLatest(EMAIL, AuthenticationAttemptType.VERIFICATION_SEND))
                .thenReturn(Optional.empty());
        when(attempts.countSince(
                EMAIL,
                AuthenticationAttemptType.VERIFICATION_SEND,
                NOW.minus(AccountService.VERIFICATION_SEND_WINDOW)
        )).thenReturn((long) AccountService.VERIFICATION_SEND_LIMIT);

        assertRateLimit(
                () -> serviceAt(NOW).requestVerification(EMAIL, "long-password"),
                "VERIFICATION_RATE_LIMITED"
        );
    }

    @Test
    void capsIncorrectVerificationCodes() {
        when(attempts.countSince(
                EMAIL,
                AuthenticationAttemptType.VERIFICATION_FAILURE,
                NOW.minus(AccountService.VERIFICATION_FAILURE_WINDOW)
        )).thenReturn((long) AccountService.VERIFICATION_FAILURE_LIMIT);

        assertRateLimit(
                () -> serviceAt(NOW).verify(EMAIL, "123456"),
                "VERIFICATION_ATTEMPTS_EXCEEDED"
        );
        verify(verifications, never()).findLatest(EMAIL);
    }

    @Test
    void recordsAnIncorrectCodeAndClearsFailuresAfterSuccess() {
        EmailVerification verification = new EmailVerification(
                "verification-id",
                EMAIL,
                "password-hash",
                Hashing.sha256("verification-id:123456"),
                NOW.plusSeconds(600),
                null,
                NOW
        );
        when(verifications.findLatest(EMAIL)).thenReturn(Optional.of(verification));

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> serviceAt(NOW).verify(EMAIL, "000000"))
                .satisfies(exception -> assertThat(exception.code())
                        .isEqualTo("VERIFICATION_CODE_INVALID"));
        verify(attempts).record(
                EMAIL,
                AuthenticationAttemptType.VERIFICATION_FAILURE,
                NOW
        );

        when(verifications.consume(verification.id(), NOW)).thenReturn(true);
        when(sessions.issue(any(Account.class))).thenReturn("session-token");
        AccountService.AuthenticatedAccount authenticated = serviceAt(NOW)
                .verify(EMAIL, "123456");

        assertThat(authenticated.account().plan()).isEqualTo(Plan.FREE);
        verify(attempts).clear(EMAIL, AuthenticationAttemptType.VERIFICATION_FAILURE);
    }

    @Test
    void blocksLoginFailuresAndRecoversAfterWindow() {
        when(attempts.countSince(
                EMAIL,
                AuthenticationAttemptType.LOGIN_FAILURE,
                NOW.minus(AccountService.LOGIN_FAILURE_WINDOW)
        )).thenReturn((long) AccountService.LOGIN_FAILURE_LIMIT);

        assertRateLimit(
                () -> serviceAt(NOW).login(EMAIL, "password"),
                "LOGIN_RATE_LIMITED"
        );

        Instant recoveredAt = NOW.plus(AccountService.LOGIN_FAILURE_WINDOW).plusSeconds(1);
        Account account = new Account(
                "account-id",
                EMAIL,
                "password-hash",
                Plan.FREE,
                null,
                null,
                NOW,
                NOW
        );
        when(attempts.countSince(
                EMAIL,
                AuthenticationAttemptType.LOGIN_FAILURE,
                recoveredAt.minus(AccountService.LOGIN_FAILURE_WINDOW)
        )).thenReturn(0L);
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "password-hash")).thenReturn(true);
        when(sessions.issue(account)).thenReturn("session-token");

        AccountService.AuthenticatedAccount authenticated = serviceAt(recoveredAt)
                .login(EMAIL, "password");

        assertThat(authenticated.sessionToken()).isEqualTo("session-token");
        verify(attempts).clear(EMAIL, AuthenticationAttemptType.LOGIN_FAILURE);
    }

    private AccountService serviceAt(Instant now) {
        return new AccountService(
                accounts,
                verifications,
                attempts,
                mailer,
                sessions,
                passwordEncoder,
                random,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private void assertRateLimit(Runnable action, String expectedCode) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> {
                    assertThat(exception.status().value()).isEqualTo(429);
                    assertThat(exception.code()).isEqualTo(expectedCode);
                });
    }
}
