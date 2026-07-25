package com.mysend.account;

import jakarta.servlet.http.HttpServletRequest;
import com.mysend.security.CookieSupport;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountSessionService {

    public static final String SESSION_COOKIE = "mysend_session";
    public static final Duration SESSION_DURATION = Duration.ofDays(30);

    private final AppSessionRepository sessions;
    private final AccountRepository accounts;
    private final SecureRandom random;
    private final Clock clock;

    public AccountSessionService(
            AppSessionRepository sessions,
            AccountRepository accounts,
            SecureRandom random,
            Clock clock
    ) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.random = random;
        this.clock = clock;
    }

    public String issue(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.insert(
                UUID.randomUUID().toString(),
                account.id(),
                token,
                clock.instant().plus(SESSION_DURATION),
                clock.instant()
        );
        return token;
    }

    public Optional<Account> current(HttpServletRequest request) {
        return CookieSupport.read(request, SESSION_COOKIE)
                .flatMap(token -> sessions.findAccountId(token, clock.instant()))
                .flatMap(accounts::findById);
    }

    public void revoke(HttpServletRequest request) {
        CookieSupport.read(request, SESSION_COOKIE).ifPresent(sessions::delete);
    }
}
