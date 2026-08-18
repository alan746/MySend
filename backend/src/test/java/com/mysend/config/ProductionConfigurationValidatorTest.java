package com.mysend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsCompleteProductionConfiguration() {
        AppProperties properties = properties(
                List.of("https://mysend.app"),
                "https://mysend.app",
                true,
                Path.of("/app/uploads"),
                "MySend <send@mysend.app>",
                true,
                false,
                true,
                new AppProperties.Stripe("sk_live_value", "whsec_value", "price_value")
        );
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/mysend");
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(properties, environment);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryUnsafeProductionDefaultAtStartup() {
        AppProperties properties = properties(
                List.of("http://localhost:3000"),
                "http://localhost:3000",
                false,
                Path.of("uploads"),
                "MySend <no-reply@example.com>",
                false,
                true,
                false,
                new AppProperties.Stripe("", "", "")
        );
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:file:./data/mysend");
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(properties, environment);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .satisfies(exception -> assertThat(exception.getMessage())
                        .contains(
                                "DATABASE_URL must use PostgreSQL",
                                "COOKIE_SECURE must be true",
                                "APP_BASE_URL must use HTTPS",
                                "WEB_ORIGINS must contain only HTTPS origins",
                                "UPLOAD_DIRECTORY must be an absolute path",
                                "STORAGE_PERSISTENT must be true",
                                "MAIL_DELIVERY_ENABLED must be true",
                                "DEVELOPMENT_CODE_ENABLED must be false",
                                "MAIL_FROM must use a verified sender",
                                "STRIPE_SECRET_KEY is required",
                                "STRIPE_WEBHOOK_SECRET is required",
                                "STRIPE_PREMIUM_PRICE_ID is required"
                        ));
    }

    private AppProperties properties(
            List<String> webOrigins,
            String appBaseUrl,
            boolean cookieSecure,
            Path uploadDirectory,
            String mailFrom,
            boolean mailDeliveryEnabled,
            boolean developmentCodeEnabled,
            boolean storagePersistent,
            AppProperties.Stripe stripe
    ) {
        return new AppProperties(
                webOrigins,
                appBaseUrl,
                cookieSecure,
                uploadDirectory,
                mailFrom,
                mailDeliveryEnabled,
                developmentCodeEnabled,
                storagePersistent,
                stripe
        );
    }
}
