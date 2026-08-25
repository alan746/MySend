package com.mysend.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysend.account.Account;
import com.mysend.account.AccountRepository;
import com.mysend.common.ApiException;
import com.mysend.config.AppProperties;
import com.mysend.room.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final String WEBHOOK_SECRET = "whsec_test_value";

    private AccountRepository accounts;
    private StripeEventRepository events;
    private MockRestServiceServer server;
    private StripeService stripe;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        events = mock(StripeEventRepository.class);
        when(events.claim(any(), any(), any(), any())).thenReturn(true);
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        stripe = new StripeService(
                properties(),
                accounts,
                events,
                new ObjectMapper(),
                restClientBuilder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsCheckoutWithAccountMetadata() {
        server.expect(once(), requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuthorization()))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("mode=subscription")))
                .andExpect(content().string(containsString("client_reference_id=account-1")))
                .andExpect(content().string(containsString(
                        "subscription_data%5Bmetadata%5D%5Baccount_id%5D=account-1"
                )))
                .andExpect(content().string(containsString("customer_email=person%40example.com")))
                .andRespond(withSuccess(
                        "{\"url\":\"https://checkout.stripe.com/c/pay\"}",
                        MediaType.APPLICATION_JSON
                ));

        String url = stripe.createCheckout(account(null));

        assertThat(url).isEqualTo("https://checkout.stripe.com/c/pay");
        server.verify();
    }

    @Test
    void createsPortalForExistingStripeCustomer() {
        server.expect(once(), requestTo("https://api.stripe.com/v1/billing_portal/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("customer=cus_123")))
                .andExpect(content().string(containsString(
                        "return_url=https%3A%2F%2Fmysend.app%2Fsettings"
                )))
                .andRespond(withSuccess(
                        "{\"url\":\"https://billing.stripe.com/p/session\"}",
                        MediaType.APPLICATION_JSON
                ));

        String url = stripe.createPortal(account("cus_123"));

        assertThat(url).isEqualTo("https://billing.stripe.com/p/session");
        server.verify();
    }

    @Test
    void reportsMaintenanceWithoutCallingStripeWhenBillingIsDisabled() {
        StripeService disabledStripe = new StripeService(
                properties(false),
                accounts,
                events,
                new ObjectMapper(),
                RestClient.builder(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> disabledStripe.createCheckout(account(null)))
                .satisfies(exception -> {
                    assertThat(exception.status().value()).isEqualTo(503);
                    assertThat(exception.code()).isEqualTo("BILLING_UPDATING");
                    assertThat(exception.getMessage()).isEqualTo("Premium is being updated");
                });
    }

    @Test
    void rejectsInvalidWebhookSignature() {
        String payload = eventPayload("evt_invalid", "active");

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> stripe.handleWebhook(
                        payload,
                        "t=" + NOW.getEpochSecond() + ",v1=invalid"
                ))
                .satisfies(exception -> assertThat(exception.code())
                        .isEqualTo("WEBHOOK_SIGNATURE_INVALID"));
        verify(events, never()).claim(any(), any(), any(), any());
    }

    @Test
    void ignoresDuplicateWebhookDelivery() {
        String payload = """
                {
                  "id": "evt_checkout",
                  "created": %d,
                  "type": "checkout.session.completed",
                  "data": {"object": {
                    "mode": "subscription",
                    "payment_status": "paid",
                    "client_reference_id": "account-1",
                    "customer": "cus_123",
                    "subscription": "sub_123"
                  }}
                }
                """.formatted(NOW.getEpochSecond());
        when(events.claim(
                eq("evt_checkout"),
                eq("checkout.session.completed"),
                any(),
                any()
        )).thenReturn(true, false);
        String signature = signature(payload);

        stripe.handleWebhook(payload, signature);
        stripe.handleWebhook(payload, signature);

        verify(accounts).activatePremium("account-1", "cus_123", "sub_123", NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "trialing", "past_due"})
    void keepsPremiumForProvisionedSubscriptionStatuses(String status) {
        String payload = eventPayload("evt_" + status, status);

        stripe.handleWebhook(payload, signature(payload));

        verify(accounts).activatePremium("account-1", "cus_123", "sub_123", NOW);
        verify(accounts, never()).deactivateSubscription(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"canceled", "unpaid", "incomplete", "incomplete_expired", "paused"})
    void removesPremiumForInactiveSubscriptionStatuses(String status) {
        String payload = eventPayload("evt_" + status, status);

        stripe.handleWebhook(payload, signature(payload));

        verify(accounts).deactivateSubscription("sub_123", NOW);
        verify(accounts, never()).activatePremium(any(), any(), any(), any());
    }

    private String eventPayload(String eventId, String status) {
        return """
                {
                  "id": "%s",
                  "created": %d,
                  "type": "customer.subscription.updated",
                  "data": {"object": {
                    "id": "sub_123",
                    "customer": "cus_123",
                    "status": "%s",
                    "metadata": {"account_id": "account-1"}
                  }}
                }
                """.formatted(eventId, NOW.getEpochSecond(), status);
    }

    private String signature(String payload) {
        long timestamp = NOW.getEpochSecond();
        return "t=" + timestamp + ",v1="
                + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String basicAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "sk_test_value:".getBytes(StandardCharsets.UTF_8)
        );
    }

    private Account account(String customerId) {
        return new Account(
                "account-1",
                "person@example.com",
                "password-hash",
                Plan.FREE,
                customerId,
                null,
                NOW,
                NOW
        );
    }

    private AppProperties properties() {
        return properties(true);
    }

    private AppProperties properties(boolean billingEnabled) {
        return new AppProperties(
                List.of("https://mysend.app"),
                "https://mysend.app",
                true,
                Path.of("/app/uploads"),
                "MySend <send@mysend.app>",
                true,
                false,
                true,
                billingEnabled,
                new AppProperties.Resend(
                        "re_test_value",
                        "https://api.resend.com",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)
                ),
                new AppProperties.Stripe(
                        "sk_test_value",
                        WEBHOOK_SECRET,
                        "price_premium"
                )
        );
    }
}
