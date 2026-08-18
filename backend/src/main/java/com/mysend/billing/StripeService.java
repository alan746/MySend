package com.mysend.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.account.Account;
import com.mysend.account.AccountRepository;
import com.mysend.common.ApiException;
import com.mysend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class StripeService {

    private static final Set<String> PREMIUM_STATUSES = Set.of(
            "active",
            "trialing",
            "past_due"
    );
    private static final Set<String> COMPLETED_PAYMENT_STATUSES = Set.of(
            "paid",
            "no_payment_required"
    );
    private static final Set<String> SUBSCRIPTION_EVENTS = Set.of(
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.resumed",
            "customer.subscription.paused"
    );

    private final AppProperties properties;
    private final AccountRepository accounts;
    private final StripeEventRepository events;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;

    public StripeService(
            AppProperties properties,
            AccountRepository accounts,
            StripeEventRepository events,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            Clock clock
    ) {
        this.properties = properties;
        this.accounts = accounts;
        this.events = events;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl("https://api.stripe.com").build();
        this.clock = clock;
    }

    public String createCheckout(Account account) {
        var stripe = properties.stripe();
        if (blank(stripe.secretKey()) || blank(stripe.premiumPriceId())) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_NOT_CONFIGURED",
                    "Premium checkout is not configured yet"
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", stripe.premiumPriceId());
        form.add("line_items[0][quantity]", "1");
        form.add("client_reference_id", account.id());
        form.add("subscription_data[metadata][account_id]", account.id());
        if (blank(account.stripeCustomerId())) {
            form.add("customer_email", account.email());
        } else {
            form.add("customer", account.stripeCustomerId());
        }
        form.add("success_url", properties.appBaseUrl() + "/settings?payment=success");
        form.add("cancel_url", properties.appBaseUrl() + "/settings?payment=cancelled");

        JsonNode response = postForm("/v1/checkout/sessions", form);
        String url = response.path("url").asText();
        if (url.isBlank()) {
            throw billingUnavailable();
        }
        return url;
    }

    public String createPortal(Account account) {
        var stripe = properties.stripe();
        if (blank(stripe.secretKey())) {
            throw billingNotConfigured();
        }
        if (blank(account.stripeCustomerId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BILLING_PROFILE_NOT_FOUND",
                    "Start Premium checkout before opening billing management"
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", account.stripeCustomerId());
        form.add("return_url", properties.appBaseUrl() + "/settings");
        JsonNode response = postForm("/v1/billing_portal/sessions", form);
        String url = response.path("url").asText();
        if (url.isBlank()) {
            throw billingUnavailable();
        }
        return url;
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        verifyWebhook(payload, signatureHeader);
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = event.path("id").asText();
            String type = event.path("type").asText();
            if (blank(eventId) || blank(type)) {
                throw webhookInvalid();
            }
            Instant eventCreatedAt = event.path("created").canConvertToLong()
                    ? Instant.ofEpochSecond(event.path("created").asLong())
                    : clock.instant();
            if (!events.claim(
                    eventId,
                    type,
                    eventCreatedAt,
                    clock.instant()
            )) {
                return;
            }
            JsonNode object = event.path("data").path("object");
            if ("checkout.session.completed".equals(type)
                    && "subscription".equals(object.path("mode").asText())
                    && COMPLETED_PAYMENT_STATUSES.contains(
                            object.path("payment_status").asText()
                    )) {
                accounts.activatePremium(
                        object.path("client_reference_id").asText(),
                        object.path("customer").asText(),
                        object.path("subscription").asText(),
                        eventCreatedAt
                );
            } else if (SUBSCRIPTION_EVENTS.contains(type)) {
                synchronizeSubscription(object, eventCreatedAt);
            } else if ("customer.subscription.deleted".equals(type)) {
                accounts.deactivateSubscription(object.path("id").asText(), eventCreatedAt);
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw webhookInvalid();
        }
    }

    private void synchronizeSubscription(JsonNode subscription, Instant eventCreatedAt) {
        String subscriptionId = subscription.path("id").asText();
        if (PREMIUM_STATUSES.contains(subscription.path("status").asText())) {
            accounts.activatePremium(
                    subscription.path("metadata").path("account_id").asText(),
                    subscription.path("customer").asText(),
                    subscriptionId,
                    eventCreatedAt
            );
        } else {
            accounts.deactivateSubscription(subscriptionId, eventCreatedAt);
        }
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form) {
        try {
            JsonNode response = restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBasicAuth(
                            properties.stripe().secretKey(),
                            ""
                    ))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw billingUnavailable();
            }
            return response;
        } catch (RestClientException exception) {
            throw billingUnavailable();
        }
    }

    private void verifyWebhook(String payload, String signatureHeader) {
        String secret = properties.stripe().webhookSecret();
        if (blank(secret) || blank(signatureHeader)) {
            throw invalidSignature();
        }
        long timestamp = -1;
        List<String> signatures = new ArrayList<>();
        try {
            for (String item : signatureHeader.split(",")) {
                item = item.strip();
                String[] parts = item.split("=", 2);
                if (parts.length == 2 && "t".equals(parts[0])) {
                    timestamp = Long.parseLong(parts[1]);
                } else if (parts.length == 2 && "v1".equals(parts[0])) {
                    signatures.add(parts[1]);
                }
            }
        } catch (NumberFormatException exception) {
            throw invalidSignature();
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < 0 || Math.abs(now - timestamp) > 300 || signatures.isEmpty()) {
            throw invalidSignature();
        }
        String expected = hmac(secret, timestamp + "." + payload);
        boolean matches = signatures.stream().anyMatch(signature -> MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII)
        ));
        if (!matches) {
            throw invalidSignature();
        }
    }

    private static String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is required by the Java runtime", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException invalidSignature() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "WEBHOOK_SIGNATURE_INVALID",
                "The billing webhook signature is invalid"
        );
    }

    private static ApiException webhookInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "WEBHOOK_INVALID",
                "The billing event could not be processed"
        );
    }

    private static ApiException billingNotConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_NOT_CONFIGURED",
                "Premium billing is not configured yet"
        );
    }

    private static ApiException billingUnavailable() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "BILLING_UNAVAILABLE",
                "Premium billing could not be opened"
        );
    }
}
