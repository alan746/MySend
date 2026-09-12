package com.mysend.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEmailTemplateTest {

    @Test
    void rendersConciseRegistrationEmail() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.accountVerification("123456");

        assertThat(message.subject()).isEqualTo("Your MySend verification code: 123456");
        assertThat(message.html())
                .contains("<!doctype html>")
                .contains("Your verification code is:")
                .contains("123456")
                .contains("10 minutes")
                .doesNotContain("<img")
                .doesNotContain("href=");
        assertThat(message.text())
                .contains("123456")
                .contains("This code expires in 10 minutes")
                .contains("If you did not create a MySend account");
    }

    @Test
    void rendersDistinctPasswordSecurityCopy() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.passwordChange("654321");

        assertThat(message.subject()).isEqualTo("Your MySend password code: 654321");
        assertThat(message.html())
                .contains("Your password code is:")
                .contains("654321")
                .contains("you can ignore this email")
                .doesNotContain("href=");
        assertThat(message.text())
                .contains("654321")
                .contains("you can ignore this email");
    }

    @Test
    void escapesDynamicValuesInHtml() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.accountVerification("<12345");

        assertThat(message.html())
                .contains("&lt;12345")
                .doesNotContain("<12345");
    }
}
