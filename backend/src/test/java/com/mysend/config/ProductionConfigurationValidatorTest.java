package com.mysend.config;

import com.mysend.file.StorageProperties;
import com.mysend.operations.OperationsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.time.Duration;
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
                absoluteUploadDirectory(),
                "MySend <send@mysend.app>",
                true,
                false,
                true,
                true,
                resend("re_live_value", "https://api.resend.com"),
                new AppProperties.Stripe("sk_live_value", "whsec_value", "price_value")
        );
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/mysend")
                .withProperty(
                        "mysend.room-abuse.hash-key",
                        "production-room-abuse-hash-key-1234567890"
                );
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(
                        properties,
                        s3Storage(),
                        operations("production-operations-token-1234567890"),
                        environment
                );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsProductionConfigurationWithoutStripeWhileBillingIsDisabled() {
        AppProperties properties = properties(
                List.of("https://mysend.app"),
                "https://mysend.app",
                true,
                absoluteUploadDirectory(),
                "MySend <send@mysend.app>",
                true,
                false,
                true,
                false,
                resend("re_live_value", "https://api.resend.com"),
                new AppProperties.Stripe("", "", "")
        );
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/mysend")
                .withProperty(
                        "mysend.room-abuse.hash-key",
                        "production-room-abuse-hash-key-1234567890"
                );
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(
                        properties,
                        s3Storage(),
                        operations("production-operations-token-1234567890"),
                        environment
                );

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
                true,
                resend("", "http://api.resend.com"),
                new AppProperties.Stripe("", "", "")
        );
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:file:./data/mysend");
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(
                        properties,
                        localStorage(),
                        operations("local-operations-token"),
                        environment
                );

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .satisfies(exception -> assertThat(exception.getMessage())
                        .contains(
                                "DATABASE_URL must use PostgreSQL",
                                "COOKIE_SECURE must be true",
                                "APP_BASE_URL must use HTTPS",
                                "WEB_ORIGINS must contain only HTTPS origins",
                                "STORAGE_TYPE must be s3",
                                "OPERATIONS_METRICS_TOKEN must contain at least 32 non-default characters",
                                "MAIL_DELIVERY_ENABLED must be true",
                                "DEVELOPMENT_CODE_ENABLED must be false",
                                "MAIL_FROM must use a verified sender",
                                "RESEND_API_KEY is required",
                                "RESEND_API_BASE_URL must use HTTPS",
                                "ROOM_ABUSE_HASH_KEY must contain at least 32 non-default characters",
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
            boolean billingEnabled,
            AppProperties.Resend resend,
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
                billingEnabled,
                resend,
                stripe
        );
    }

    private AppProperties.Resend resend(String apiKey, String baseUrl) {
        return new AppProperties.Resend(
                apiKey,
                baseUrl,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
        );
    }

    private Path absoluteUploadDirectory() {
        return Path.of(System.getProperty("user.dir"), "app", "uploads")
                .toAbsolutePath();
    }

    private StorageProperties s3Storage() {
        return new StorageProperties(
                "s3",
                Duration.ofHours(1),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                10L * 1024 * 1024 * 1024,
                new StorageProperties.S3(
                        "https://storage.railway.app",
                        "auto",
                        "mysend-files-example",
                        "access-key",
                        "secret-key",
                        "virtual",
                        "uploads"
                )
        );
    }

    private StorageProperties localStorage() {
        return new StorageProperties(
                "local",
                Duration.ofHours(1),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                10L * 1024 * 1024 * 1024,
                null
        );
    }

    private OperationsProperties operations(String token) {
        return new OperationsProperties(
                token,
                Duration.ofMinutes(30),
                Duration.ofMinutes(30)
        );
    }
}
