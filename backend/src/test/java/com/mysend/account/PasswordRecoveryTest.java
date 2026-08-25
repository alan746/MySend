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

class PasswordRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String EMAIL = "person@example.com";
    private static final String CODE = "123456";

    private AccountRepository accounts;
    private EmailVerificationRepository emailVerifications;
    private PasswordVerificationRepository passwordVerifications;
    private AuthenticationAttemptRepository attempts;
    private VerificationMailer mailer;
    private AccountSessionService sessions;
    private PasswordEncoder passwordEncoder;
    private SecureRandom random;
    private Account account;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        emailVerifications = mock(EmailVerificationRepository.class);
        passwordVerifications = mock(PasswordVerificationRepository.class);
        attempts = mock(AuthenticationAttemptRepository.class);
        mailer = mock(VerificationMailer.class);
        sessions = mock(AccountSessionService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        random = mock(SecureRandom.class);
        account = new Account(
                "account-id",
                EMAIL,
                "old-password-hash",
                Plan.FREE,
                null,
                null,
                NOW.minusSeconds(3_600),
                NOW.minusSeconds(3_600)
        );
    }

    @Test
    void acceptsUnknownResetEmailWithoutSendingOrRevealingIt() {
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.empty());

        AccountService.PasswordCodeResult result = service().requestPasswordReset(EMAIL);

        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(result.developmentCode()).isNull();
        verify(attempts).record(EMAIL, AuthenticationAttemptType.PASSWORD_SEND, NOW);
        verify(mailer, never()).deliverPasswordCode(any(), any());
    }

    @Test
    void replacesPasswordAndSessionsWithAValidSingleUseCode() {
        PasswordVerification verification = verification(NOW.plusSeconds(600));
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordVerifications.findLatest(EMAIL, PasswordVerificationPurpose.RESET))
                .thenReturn(Optional.of(verification));
        when(passwordVerifications.consume(verification.id(), NOW)).thenReturn(true);
        when(passwordEncoder.encode("new-secure-password")).thenReturn("new-password-hash");
        when(sessions.replace(any(Account.class))).thenReturn("new-session-token");

        AccountService.AuthenticatedAccount authenticated = service().resetPassword(
                EMAIL,
                CODE,
                "new-secure-password"
        );

        assertThat(authenticated.account().passwordHash()).isEqualTo("new-password-hash");
        assertThat(authenticated.sessionToken()).isEqualTo("new-session-token");
        verify(accounts).updatePassword("account-id", "new-password-hash", NOW);
        verify(attempts).clear(EMAIL, AuthenticationAttemptType.PASSWORD_FAILURE);
    }

    @Test
    void rejectsExpiredPasswordCode() {
        PasswordVerification expired = verification(NOW);
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordVerifications.findLatest(EMAIL, PasswordVerificationPurpose.RESET))
                .thenReturn(Optional.of(expired));

        assertInvalidCode(() -> service().resetPassword(
                EMAIL,
                CODE,
                "new-secure-password"
        ));
        verify(passwordVerifications, never()).consume(any(), any());
    }

    @Test
    void rejectsPasswordCodeThatWasAlreadyConsumed() {
        PasswordVerification verification = verification(NOW.plusSeconds(600));
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordVerifications.findLatest(EMAIL, PasswordVerificationPurpose.RESET))
                .thenReturn(Optional.of(verification));
        when(passwordVerifications.consume(verification.id(), NOW)).thenReturn(false);

        assertInvalidCode(() -> service().resetPassword(
                EMAIL,
                CODE,
                "new-secure-password"
        ));
        verify(accounts, never()).updatePassword(any(), any(), any());
    }

    private PasswordVerification verification(Instant expiresAt) {
        return new PasswordVerification(
                "verification-id",
                account.id(),
                EMAIL,
                PasswordVerificationPurpose.RESET,
                Hashing.sha256("verification-id:" + CODE),
                expiresAt,
                null,
                NOW
        );
    }

    private AccountService service() {
        return new AccountService(
                accounts,
                emailVerifications,
                passwordVerifications,
                attempts,
                mailer,
                sessions,
                passwordEncoder,
                random,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void assertInvalidCode(Runnable action) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> assertThat(exception.code())
                        .isEqualTo("PASSWORD_CODE_INVALID"));
    }
}
