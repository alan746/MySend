package com.mysend.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("mysend.operations")
public record OperationsProperties(
        String metricsToken,
        Duration cleanupStaleAfter,
        Duration deletionBacklogWarning
) {
}
