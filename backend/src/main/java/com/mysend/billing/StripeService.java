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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

@Service
public class StripeService {

    private final AppProperties properties;
    private final AccountRepository accounts;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;

    public StripeService(
            AppProperties properties,
            AccountRepository accounts,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            Clock clock
    ) {
        this.properties = properties;
        this.accounts = accounts;
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
        form.add("customer_email", account.email());
        form.add("success_url", properties.appBaseUrl() + "/settings?payment=success");
        form.add("cancel_url", properties.appBaseUrl() + "/settings?payment=cancelled");

        JsonNode response = restClient.post()
                .uri("/v1/checkout/sessions")
                .headers(headers -> headers.setBasicAuth(stripe.secretKey(), ""))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("url").asText().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "BILLING_UNAVAILABLE",
                    "Premium checkout could not be started"
            );
        }
        return response.path("url").asText();
    }

    public void handleWebhook(String payload, String signatureHeader) {
        verifyWebhook(payload, signatureHeader);
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("type").asText();
            JsonNode object = event.path("data").path("object");
            if ("checkout.session.completed".equals(type)
                    && "subscription".equals(object.path("mode").asText())) {
                accounts.activatePremium(
                        object.path("client_reference_id").asText(),
                        object.path("customer").asText(),
                        object.path("subscription").asText(),
                        clock.instant()
                );
            } else if ("customer.subscription.deleted".equals(type)) {
                accounts.deactivateSubscription(object.path("id").asText(), clock.instant());
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WEBHOOK_INVALID",
                    "The billing event could not be processed"
            );
        }
    }

    private void verifyWebhook(String payload, String signatureHeader) {
        String secret = properties.stripe().webhookSecret();
        if (blank(secret) || blank(signatureHeader)) {
            throw invalidSignature();
        }
        long timestamp = -1;
        String signature = null;
        for (String item : signatureHeader.split(",")) {
            String[] parts = item.split("=", 2);
            if (parts.length == 2 && "t".equals(parts[0])) {
                timestamp = Long.parseLong(parts[1]);
            } else if (parts.length == 2 && "v1".equals(parts[0])) {
                signature = parts[1];
            }
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < 0 || Math.abs(now - timestamp) > 300 || signature == null) {
            throw invalidSignature();
        }
        String expected = hmac(secret, timestamp + "." + payload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII)
        )) {
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
}
