package com.mysend.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEmailTemplateTest {

    @Test
    void rendersBrandedRegistrationEmailWithoutRemoteImages() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.accountVerification(
                        "123456",
                        "https://mysend.app/"
                );

        assertThat(message.subject()).isEqualTo("Verify your MySend email");
        assertThat(message.html())
                .contains("<!doctype html>")
                .contains("ACCOUNT VERIFICATION")
                .contains("Finish setting up your account.")
                .contains("123456")
                .contains("href=\"https://mysend.app\"")
                .contains("10 minutes")
                .doesNotContain("<img");
        assertThat(message.text())
                .contains("123456")
                .contains("This code expires in 10 minutes")
                .contains("Open MySend: https://mysend.app");
    }

    @Test
    void rendersDistinctPasswordSecurityCopy() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.passwordChange(
                        "654321",
                        "https://mysend.app"
                );

        assertThat(message.subject()).isEqualTo("Reset your MySend password");
        assertThat(message.html())
                .contains("PASSWORD SECURITY")
                .contains("Confirm your password change.")
                .contains("654321")
                .contains("your password will stay the same");
        assertThat(message.text())
                .contains("654321")
                .contains("your password will stay the same");
    }

    @Test
    void escapesDynamicValuesInHtml() {
        SecurityEmailTemplate.Message message =
                SecurityEmailTemplate.accountVerification(
                        "<12345",
                        "https://mysend.app/?next=\"signup\"&source=email"
                );

        assertThat(message.html())
                .contains("&lt;12345")
                .contains("next=&quot;signup&quot;&amp;source=email")
                .doesNotContain("<12345");
    }
}
