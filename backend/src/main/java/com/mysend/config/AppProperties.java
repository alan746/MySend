package com.mysend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
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
        Stripe stripe
) {
    public record Stripe(
            String secretKey,
            String webhookSecret,
            String premiumPriceId
    ) {
    }
}
