package com.mysend.account;

import com.mysend.config.AppProperties;
import com.mysend.security.CookieSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService accounts;
    private final AccountSessionService sessions;
    private final AppProperties properties;

    public AccountController(
            AccountService accounts,
            AccountSessionService sessions,
            AppProperties properties
    ) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.properties = properties;
    }

    @PostMapping("/register/code")
    AccountService.VerificationResult requestCode(
            @Valid @RequestBody RegistrationRequest request
    ) {
        return accounts.requestVerification(request.email(), request.password());
    }

    @PostMapping("/register/verify")
    ResponseEntity<AccountView> verify(
            @Valid @RequestBody VerificationRequest request
    ) {
        return authenticated(accounts.verify(request.email(), request.code()));
    }

    @PostMapping("/login")
    ResponseEntity<AccountView> login(@Valid @RequestBody LoginRequest request) {
        return authenticated(accounts.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    ResponseEntity<AccountView> current(HttpServletRequest request) {
        return sessions.current(request)
                .map(account -> ResponseEntity.ok(AccountView.from(account)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.revoke(request);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        CookieSupport.httpOnly(
                                AccountSessionService.SESSION_COOKIE,
                                "",
                                Duration.ZERO,
                                properties.cookieSecure()
                        ).toString()
                )
                .build();
    }

    private ResponseEntity<AccountView> authenticated(
            AccountService.AuthenticatedAccount authenticated
    ) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        CookieSupport.httpOnly(
                                AccountSessionService.SESSION_COOKIE,
                                authenticated.sessionToken(),
                                AccountSessionService.SESSION_DURATION,
                                properties.cookieSecure()
                        ).toString()
                )
                .body(AccountView.from(authenticated.account()));
    }

    record RegistrationRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 100) String password
    ) {
    }

    record VerificationRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Pattern(regexp = "\\d{6}") String code
    ) {
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String password
    ) {
    }

    public record AccountView(
            String id,
            String email,
            String plan,
            int activeRoomLimit,
            int roomMinutes,
            int clipboardCharacters,
            long roomFileBytes
    ) {
        static AccountView from(Account account) {
            var limits = account.plan().limits();
            return new AccountView(
                    account.id(),
                    account.email(),
                    account.plan().name(),
                    limits.activeRooms(),
                    limits.maximumLifetimeMinutes(),
                    limits.clipboardCharacters(),
                    limits.roomFileBytes()
            );
        }
    }
}
