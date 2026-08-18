package com.mysend.billing;

import com.mysend.account.AccountSessionService;
import com.mysend.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final StripeService stripe;
    private final AccountSessionService sessions;

    public BillingController(StripeService stripe, AccountSessionService sessions) {
        this.stripe = stripe;
        this.sessions = sessions;
    }

    @PostMapping("/checkout")
    CheckoutResponse checkout(HttpServletRequest request) {
        var account = currentAccount(request);
        return new CheckoutResponse(stripe.createCheckout(account));
    }

    @PostMapping("/portal")
    CheckoutResponse portal(HttpServletRequest request) {
        return new CheckoutResponse(stripe.createPortal(currentAccount(request)));
    }

    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature
    ) {
        stripe.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    public record CheckoutResponse(String url) {
    }

    private com.mysend.account.Account currentAccount(HttpServletRequest request) {
        return sessions.current(request).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "SIGN_IN_REQUIRED",
                "Sign in before managing Premium"
        ));
    }
}
