package com.mysend.account;

import com.mysend.common.ApiException;
import com.mysend.common.Hashing;
import com.mysend.room.Plan;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    static final Duration VERIFICATION_RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration VERIFICATION_SEND_WINDOW = Duration.ofHours(1);
    static final int VERIFICATION_SEND_LIMIT = 5;
    static final Duration VERIFICATION_FAILURE_WINDOW = Duration.ofMinutes(10);
    static final int VERIFICATION_FAILURE_LIMIT = 5;
    static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int LOGIN_FAILURE_LIMIT = 5;

    private final AccountRepository accounts;
    private final EmailVerificationRepository verifications;
    private final PasswordVerificationRepository passwordVerifications;
    private final AuthenticationAttemptRepository attempts;
    private final VerificationMailer mailer;
    private final AccountSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random;
    private final Clock clock;

    public AccountService(
            AccountRepository accounts,
            EmailVerificationRepository verifications,
            PasswordVerificationRepository passwordVerifications,
            AuthenticationAttemptRepository attempts,
            VerificationMailer mailer,
            AccountSessionService sessions,
            PasswordEncoder passwordEncoder,
            SecureRandom random,
            Clock clock
    ) {
        this.accounts = accounts;
        this.verifications = verifications;
        this.passwordVerifications = passwordVerifications;
        this.attempts = attempts;
        this.mailer = mailer;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public VerificationResult requestVerification(String emailValue, String password) {
        String email = normalizeEmail(emailValue);
        if (accounts.existsByEmail(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "This email is already registered"
            );
        }
        Instant now = clock.instant();
        enforceVerificationSendLimit(email, now);
        attempts.record(email, AuthenticationAttemptType.VERIFICATION_SEND, now);
        String id = UUID.randomUUID().toString();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        EmailVerification verification = new EmailVerification(
                id,
                email,
                passwordEncoder.encode(password),
                Hashing.sha256(id + ":" + code),
                now.plus(Duration.ofMinutes(10)),
                null,
                now
        );
        verifications.insert(verification);
        boolean delivered = mailer.deliver(email, code);
        if (!delivered && !mailer.canExposeDevelopmentCode()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_NOT_CONFIGURED",
                    "Verification email delivery is not configured"
            );
        }
        return new VerificationResult(
                verification.expiresAt(),
                delivered,
                mailer.canExposeDevelopmentCode() ? code : null
        );
    }

    @Transactional
    public AuthenticatedAccount verify(String emailValue, String code) {
        String email = normalizeEmail(emailValue);
        if (accounts.existsByEmail(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "This email is already registered"
            );
        }
        Instant now = clock.instant();
        enforceFailureLimit(
                email,
                AuthenticationAttemptType.VERIFICATION_FAILURE,
                now.minus(VERIFICATION_FAILURE_WINDOW),
                VERIFICATION_FAILURE_LIMIT,
                "VERIFICATION_ATTEMPTS_EXCEEDED",
                "Too many incorrect codes. Try again in 10 minutes"
        );
        EmailVerification verification = verifications.findLatest(email).orElse(null);
        if (verification == null
                || !verification.canUseAt(now)
                || !verification.codeHash().equals(
                        Hashing.sha256(verification.id() + ":" + code)
                )) {
            attempts.record(email, AuthenticationAttemptType.VERIFICATION_FAILURE, now);
            throw invalidCode();
        }
        if (!verifications.consume(verification.id(), now)) {
            attempts.record(email, AuthenticationAttemptType.VERIFICATION_FAILURE, now);
            throw invalidCode();
        }
        Account account = new Account(
                UUID.randomUUID().toString(),
                email,
                verification.passwordHash(),
                Plan.FREE,
                null,
                null,
                now,
                now
        );
        accounts.insert(account);
        attempts.clear(email, AuthenticationAttemptType.VERIFICATION_FAILURE);
        return new AuthenticatedAccount(account, sessions.issue(account));
    }

    public AuthenticatedAccount login(String emailValue, String password) {
        String email = normalizeEmail(emailValue);
        Instant now = clock.instant();
        enforceFailureLimit(
                email,
                AuthenticationAttemptType.LOGIN_FAILURE,
                now.minus(LOGIN_FAILURE_WINDOW),
                LOGIN_FAILURE_LIMIT,
                "LOGIN_RATE_LIMITED",
                "Too many login attempts. Try again in 15 minutes"
        );
        Account account = accounts.findByEmail(email).orElse(null);
        if (account == null || !passwordEncoder.matches(password, account.passwordHash())) {
            attempts.record(email, AuthenticationAttemptType.LOGIN_FAILURE, now);
            throw invalidCredentials();
        }
        attempts.clear(email, AuthenticationAttemptType.LOGIN_FAILURE);
        return new AuthenticatedAccount(account, sessions.issue(account));
    }

    @Transactional
    public PasswordCodeResult requestPasswordReset(String emailValue) {
        String email = normalizeEmail(emailValue);
        Instant now = clock.instant();
        enforcePasswordSendLimit(email, now);
        attempts.record(email, AuthenticationAttemptType.PASSWORD_SEND, now);
        Account account = accounts.findByEmail(email).orElse(null);
        if (account == null) {
            return new PasswordCodeResult(now.plus(Duration.ofMinutes(10)), null);
        }
        return createPasswordVerification(
                account,
                PasswordVerificationPurpose.RESET,
                now,
                true
        );
    }

    @Transactional
    public PasswordCodeResult requestPasswordChange(Account account) {
        Instant now = clock.instant();
        enforcePasswordSendLimit(account.email(), now);
        attempts.record(account.email(), AuthenticationAttemptType.PASSWORD_SEND, now);
        return createPasswordVerification(
                account,
                PasswordVerificationPurpose.CHANGE,
                now,
                false
        );
    }

    @Transactional
    public AuthenticatedAccount resetPassword(
            String emailValue,
            String code,
            String newPassword
    ) {
        String email = normalizeEmail(emailValue);
        Account account = accounts.findByEmail(email).orElse(null);
        return completePassword(
                account,
                email,
                PasswordVerificationPurpose.RESET,
                code,
                newPassword
        );
    }

    @Transactional
    public AuthenticatedAccount changePassword(
            Account account,
            String code,
            String newPassword
    ) {
        return completePassword(
                account,
                account.email(),
                PasswordVerificationPurpose.CHANGE,
                code,
                newPassword
        );
    }

    private PasswordCodeResult createPasswordVerification(
            Account account,
            PasswordVerificationPurpose purpose,
            Instant now,
            boolean hideDeliveryFailure
    ) {
        String id = UUID.randomUUID().toString();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        PasswordVerification verification = new PasswordVerification(
                id,
                account.id(),
                account.email(),
                purpose,
                Hashing.sha256(id + ":" + code),
                now.plus(Duration.ofMinutes(10)),
                null,
                now
        );
        passwordVerifications.insert(verification);
        boolean delivered;
        try {
            delivered = mailer.deliverPasswordCode(account.email(), code);
        } catch (ApiException exception) {
            if (!hideDeliveryFailure) {
                throw exception;
            }
            delivered = false;
        }
        if (!delivered && !mailer.canExposeDevelopmentCode() && !hideDeliveryFailure) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_NOT_CONFIGURED",
                    "Password email delivery is not configured"
            );
        }
        return new PasswordCodeResult(
                verification.expiresAt(),
                mailer.canExposeDevelopmentCode() ? code : null
        );
    }

    private AuthenticatedAccount completePassword(
            Account account,
            String email,
            PasswordVerificationPurpose purpose,
            String code,
            String newPassword
    ) {
        Instant now = clock.instant();
        enforceFailureLimit(
                email,
                AuthenticationAttemptType.PASSWORD_FAILURE,
                now.minus(VERIFICATION_FAILURE_WINDOW),
                VERIFICATION_FAILURE_LIMIT,
                "PASSWORD_ATTEMPTS_EXCEEDED",
                "Too many incorrect codes. Try again in 10 minutes"
        );
        PasswordVerification verification = passwordVerifications
                .findLatest(email, purpose)
                .orElse(null);
        if (account == null
                || verification == null
                || !account.id().equals(verification.accountId())
                || !verification.canUseAt(now)
                || !verification.codeHash().equals(
                        Hashing.sha256(verification.id() + ":" + code)
                )) {
            attempts.record(email, AuthenticationAttemptType.PASSWORD_FAILURE, now);
            throw invalidPasswordCode();
        }
        if (!passwordVerifications.consume(verification.id(), now)) {
            attempts.record(email, AuthenticationAttemptType.PASSWORD_FAILURE, now);
            throw invalidPasswordCode();
        }
        String passwordHash = passwordEncoder.encode(newPassword);
        accounts.updatePassword(account.id(), passwordHash, now);
        Account updated = new Account(
                account.id(),
                account.email(),
                passwordHash,
                account.plan(),
                account.stripeCustomerId(),
                account.stripeSubscriptionId(),
                account.createdAt(),
                now
        );
        attempts.clear(email, AuthenticationAttemptType.PASSWORD_FAILURE);
        return new AuthenticatedAccount(updated, sessions.replace(updated));
    }

    private void enforceVerificationSendLimit(String email, Instant now) {
        attempts.findLatest(email, AuthenticationAttemptType.VERIFICATION_SEND)
                .filter(lastAttempt -> lastAttempt.plus(VERIFICATION_RESEND_COOLDOWN).isAfter(now))
                .ifPresent(lastAttempt -> {
                    throw rateLimit(
                            "VERIFICATION_COOLDOWN",
                            "Wait 60 seconds before requesting another code"
                    );
                });
        enforceFailureLimit(
                email,
                AuthenticationAttemptType.VERIFICATION_SEND,
                now.minus(VERIFICATION_SEND_WINDOW),
                VERIFICATION_SEND_LIMIT,
                "VERIFICATION_RATE_LIMITED",
                "Too many verification emails. Try again in one hour"
        );
    }

    private void enforcePasswordSendLimit(String email, Instant now) {
        attempts.findLatest(email, AuthenticationAttemptType.PASSWORD_SEND)
                .filter(lastAttempt -> lastAttempt.plus(VERIFICATION_RESEND_COOLDOWN).isAfter(now))
                .ifPresent(lastAttempt -> {
                    throw rateLimit(
                            "PASSWORD_CODE_COOLDOWN",
                            "Wait 60 seconds before requesting another code"
                    );
                });
        enforceFailureLimit(
                email,
                AuthenticationAttemptType.PASSWORD_SEND,
                now.minus(VERIFICATION_SEND_WINDOW),
                VERIFICATION_SEND_LIMIT,
                "PASSWORD_CODE_RATE_LIMITED",
                "Too many password emails. Try again in one hour"
        );
    }

    private void enforceFailureLimit(
            String email,
            AuthenticationAttemptType type,
            Instant since,
            int limit,
            String code,
            String message
    ) {
        if (attempts.countSince(email, type, since) >= limit) {
            throw rateLimit(code, message);
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCode() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VERIFICATION_CODE_INVALID",
                "The verification code is incorrect or expired"
        );
    }

    private static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Email or password is incorrect"
        );
    }

    private static ApiException invalidPasswordCode() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_CODE_INVALID",
                "The password code is incorrect or expired"
        );
    }

    private static ApiException rateLimit(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public record VerificationResult(
            Instant expiresAt,
            boolean delivered,
            String developmentCode
    ) {
    }

    public record PasswordCodeResult(
            Instant expiresAt,
            String developmentCode
    ) {
    }

    public record AuthenticatedAccount(Account account, String sessionToken) {
    }
}
