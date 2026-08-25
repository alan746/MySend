package com.mysend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("mysend")
public record AppProperties(
        List<String> webOrigins,
        String appBaseUrl,
        boolean cookieSecure,
        Path uploadDirectory,
        String mailFrom,
        boolean mailDeliveryEnabled,
        boolean developmentCodeEnabled,
        boolean storagePersistent,
        boolean billingEnabled,
        Resend resend,
        Stripe stripe
) {
    public record Resend(
            String apiKey,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
    }

    public record Stripe(
            String secretKey,
            String webhookSecret,
            String premiumPriceId
    ) {
    }
}
