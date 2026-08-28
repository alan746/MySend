package com.mysend.security;

import com.mysend.account.AccountSessionService;
import com.mysend.common.Hashing;
import com.mysend.config.AppProperties;
import com.mysend.room.Plan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class DeviceIdentityService {

    public static final String DEVICE_COOKIE = "mysend_device";

    private final SecureRandom random;
    private final AppProperties properties;
    private final AccountSessionService accountSessions;

    public DeviceIdentityService(
            SecureRandom random,
            AppProperties properties,
            AccountSessionService accountSessions
    ) {
        this.random = random;
        this.properties = properties;
        this.accountSessions = accountSessions;
    }

    public OwnerIdentity resolve(HttpServletRequest request, HttpServletResponse response) {
        var account = accountSessions.current(request);
        if (account.isPresent()) {
            return new OwnerIdentity(
                    "account:" + account.get().id(),
                    account.get().id(),
                    account.get().plan()
            );
        }
        String token = CookieSupport.read(request, DEVICE_COOKIE).orElseGet(() -> {
            String created = randomToken();
            response.addHeader(
                    "Set-Cookie",
                    CookieSupport.httpOnly(
                            DEVICE_COOKIE,
                            created,
                            Duration.ofDays(365),
                            properties.cookieSecure()
                    ).toString()
            );
            return created;
        });
        return new OwnerIdentity("device:" + Hashing.sha256(token), null, Plan.GUEST);
    }

    public Optional<String> provenGuestOwnerKey(HttpServletRequest request) {
        return CookieSupport.read(request, DEVICE_COOKIE)
                .map(token -> "device:" + Hashing.sha256(token));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
