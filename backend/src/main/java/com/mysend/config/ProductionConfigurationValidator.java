package com.mysend.config;

import com.mysend.file.StorageProperties;
import com.mysend.operations.OperationsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("production")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final AppProperties properties;
    private final StorageProperties storage;
    private final OperationsProperties operations;
    private final Environment environment;

    public ProductionConfigurationValidator(
            AppProperties properties,
            StorageProperties storage,
            OperationsProperties operations,
            Environment environment
    ) {
        this.properties = properties;
        this.storage = storage;
        this.operations = operations;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> problems = findProblems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration is incomplete:\n- "
                            + String.join("\n- ", problems)
            );
        }
    }

    List<String> findProblems() {
        List<String> problems = new ArrayList<>();
        String databaseUrl = environment.getProperty("spring.datasource.url", "");
        if (!databaseUrl.startsWith("jdbc:postgresql:")) {
            problems.add("DATABASE_URL must use PostgreSQL");
        }
        if (!properties.cookieSecure()) {
            problems.add("COOKIE_SECURE must be true");
        }
        if (properties.appBaseUrl() == null
                || !properties.appBaseUrl().startsWith("https://")) {
            problems.add("APP_BASE_URL must use HTTPS");
        }
        if (properties.webOrigins() == null
                || properties.webOrigins().isEmpty()
                || properties.webOrigins().stream()
                .anyMatch(origin -> origin == null || !origin.startsWith("https://"))) {
            problems.add("WEB_ORIGINS must contain only HTTPS origins");
        }
        validateStorage(problems);
        if (operations == null
                || operations.metricsToken() == null
                || operations.metricsToken().strip().length() < 32
                || operations.metricsToken().equals("local-operations-token")) {
            problems.add("OPERATIONS_METRICS_TOKEN must contain at least 32 non-default characters");
        }
        if (!properties.mailDeliveryEnabled()) {
            problems.add("MAIL_DELIVERY_ENABLED must be true");
        }
        if (properties.developmentCodeEnabled()) {
            problems.add("DEVELOPMENT_CODE_ENABLED must be false");
        }
        if (properties.mailFrom() == null
                || properties.mailFrom().isBlank()
                || properties.mailFrom().contains("example.com")) {
            problems.add("MAIL_FROM must use a verified sender");
        }
        if (properties.resend() == null || isBlank(properties.resend().apiKey())) {
            problems.add("RESEND_API_KEY is required");
        }
        if (properties.resend() == null
                || isBlank(properties.resend().baseUrl())
                || !properties.resend().baseUrl().startsWith("https://")) {
            problems.add("RESEND_API_BASE_URL must use HTTPS");
        }
        String roomAbuseHashKey = environment.getProperty(
                "mysend.room-abuse.hash-key",
                ""
        ).strip();
        if (roomAbuseHashKey.length() < 32
                || roomAbuseHashKey.equals("local-room-abuse-hash-key-change-me")) {
            problems.add("ROOM_ABUSE_HASH_KEY must contain at least 32 non-default characters");
        }
        if (properties.billingEnabled()) {
            validateStripe(problems);
        }
        return problems;
    }

    private void validateStorage(List<String> problems) {
        if (storage == null || !storage.usesS3()) {
            problems.add("STORAGE_TYPE must be s3");
            return;
        }
        StorageProperties.S3 s3 = storage.s3();
        if (s3 == null
                || isBlank(s3.endpoint())
                || !s3.endpoint().startsWith("https://")) {
            problems.add("AWS_ENDPOINT_URL must use HTTPS");
        }
        if (s3 == null || isBlank(s3.region())) {
            problems.add("AWS_DEFAULT_REGION is required");
        }
        if (s3 == null || isBlank(s3.bucket())) {
            problems.add("AWS_S3_BUCKET_NAME is required");
        }
        if (s3 == null || isBlank(s3.accessKeyId())) {
            problems.add("AWS_ACCESS_KEY_ID is required");
        }
        if (s3 == null || isBlank(s3.secretAccessKey())) {
            problems.add("AWS_SECRET_ACCESS_KEY is required");
        }
    }

    private void validateStripe(List<String> problems) {
        AppProperties.Stripe stripe = properties.stripe();
        if (stripe == null || isBlank(stripe.secretKey())) {
            problems.add("STRIPE_SECRET_KEY is required");
        }
        if (stripe == null || isBlank(stripe.webhookSecret())) {
            problems.add("STRIPE_WEBHOOK_SECRET is required");
        }
        if (stripe == null || isBlank(stripe.premiumPriceId())) {
            problems.add("STRIPE_PREMIUM_PRICE_ID is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
