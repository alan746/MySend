package com.mysend.account;

import com.mysend.common.ApiException;
import com.mysend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VerificationMailerTest {

    private MockRestServiceServer server;
    private VerificationMailer mailer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer re_test_value")
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                );
        server = MockRestServiceServer.bindTo(builder).build();
        mailer = new VerificationMailer(builder.build(), properties());
    }

    @Test
    void sendsVerificationCodeThroughResendHttpsApi() {
        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer re_test_value"
                ))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("MySend <no-reply@mysend.app>"))
                .andExpect(jsonPath("$.to[0]").value("person@example.com"))
                .andExpect(jsonPath("$.subject").value("Verify your MySend email"))
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Finish setting up your account."),
                        org.hamcrest.Matchers.containsString("123456"),
                        org.hamcrest.Matchers.containsString("https://mysend.app")
                )))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withSuccess(
                        "{\"id\":\"email-id\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(mailer.deliver("person@example.com", "123456")).isTrue();
        server.verify();
    }

    @Test
    void sendsPasswordCodeWithDistinctCopy() {
        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.subject").value("Reset your MySend password"))
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Confirm your password change."),
                        org.hamcrest.Matchers.containsString("654321")
                )))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("654321")))
                .andRespond(withSuccess(
                        "{\"id\":\"password-email-id\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(mailer.deliverPasswordCode("person@example.com", "654321")).isTrue();
        server.verify();
    }

    @Test
    void mapsResendFailureToServiceUnavailable() {
        server.expect(once(), requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> mailer.deliver("person@example.com", "123456"))
                .satisfies(exception -> {
                    assertThat(exception.status().value()).isEqualTo(503);
                    assertThat(exception.code()).isEqualTo("MAIL_UNAVAILABLE");
                });
        server.verify();
    }

    private AppProperties properties() {
        return new AppProperties(
                List.of("https://mysend.app"),
                "https://mysend.app",
                true,
                Path.of("/app/uploads"),
                "MySend <no-reply@mysend.app>",
                true,
                false,
                true,
                false,
                new AppProperties.Resend(
                        "re_test_value",
                        "https://api.resend.com",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)
                ),
                new AppProperties.Stripe("", "", "")
        );
    }
}
