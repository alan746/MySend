package com.mysend.file;

import com.mysend.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mysend.storage.type", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        return S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey())
                ))
                .forcePathStyle(s3.usesPathStyle())
                .build();
    }

    @Bean
    FileStore s3FileStore(
            S3Client client,
            StorageProperties properties,
            AppProperties appProperties
    ) throws IOException {
        return new MigratingFileStore(
                new S3FileStore(client, properties.s3()),
                new LocalFileStore(appProperties)
        );
    }
}
