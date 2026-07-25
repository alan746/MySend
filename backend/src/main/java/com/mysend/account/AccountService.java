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

    private final AccountRepository accounts;
    private final EmailVerificationRepository verifications;
    private final VerificationMailer mailer;
    private final AccountSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random;
    private final Clock clock;

    public AccountService(
            AccountRepository accounts,
            EmailVerificationRepository verifications,
            VerificationMailer mailer,
            AccountSessionService sessions,
            PasswordEncoder passwordEncoder,
            SecureRandom random,
            Clock clock
    ) {
        this.accounts = accounts;
        this.verifications = verifications;
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
        EmailVerification verification = verifications.findLatest(email)
                .orElseThrow(AccountService::invalidCode);
        if (!verification.canUseAt(now)
                || !verification.codeHash().equals(
                        Hashing.sha256(verification.id() + ":" + code)
                )) {
            throw invalidCode();
        }
        if (!verifications.consume(verification.id(), now)) {
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
        return new AuthenticatedAccount(account, sessions.issue(account));
    }

    public AuthenticatedAccount login(String emailValue, String password) {
        Account account = accounts.findByEmail(normalizeEmail(emailValue))
                .orElseThrow(AccountService::invalidCredentials);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        return new AuthenticatedAccount(account, sessions.issue(account));
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

    public record VerificationResult(
            Instant expiresAt,
            boolean delivered,
            String developmentCode
    ) {
    }

    public record AuthenticatedAccount(Account account, String sessionToken) {
    }
}
