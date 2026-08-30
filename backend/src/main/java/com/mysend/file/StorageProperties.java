package com.mysend.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("mysend.storage")
public record StorageProperties(
        String type,
        Duration orphanGracePeriod,
        Duration deleteInterval,
        Duration reconcileInterval,
        long capacityWarningBytes,
        S3 s3
) {
    public boolean usesS3() {
        return "s3".equalsIgnoreCase(type);
    }

    public record S3(
            String endpoint,
            String region,
            String bucket,
            String accessKeyId,
            String secretAccessKey,
            String urlStyle,
            String prefix
    ) {
        boolean usesPathStyle() {
            return "path".equalsIgnoreCase(urlStyle);
        }
    }
}
