package com.mysend.file;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class S3FileStore implements FileStore {

    private final S3Client client;
    private final String bucket;
    private final String prefix;

    S3FileStore(S3Client client, StorageProperties.S3 properties) {
        this.client = client;
        this.bucket = properties.bucket();
        this.prefix = normalizePrefix(properties.prefix());
    }

    @Override
    public void put(
            String storageKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) throws IOException {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(storageKey))
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build(),
                    RequestBody.fromInputStream(inputStream, contentLength)
            );
        } catch (SdkException exception) {
            throw storageFailure("store", exception);
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(storageKey))
                            .build()
            );
            return response;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new FileNotFoundException(storageKey);
            }
            throw storageFailure("read", exception);
        } catch (SdkException exception) {
            throw storageFailure("read", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(storageKey))
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw storageFailure("inspect", exception);
        } catch (SdkException exception) {
            throw storageFailure("inspect", exception);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(storageKey))
                    .build());
        } catch (SdkException exception) {
            throw storageFailure("delete", exception);
        }
    }

    @Override
    public List<StoredObject> list() throws IOException {
        List<StoredObject> objects = new ArrayList<>();
        String continuationToken = null;
        try {
            do {
                var request = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(continuationToken)
                        .build();
                var response = client.listObjectsV2(request);
                response.contents().stream()
                        .filter(object -> object.key().startsWith(prefix))
                        .forEach(object -> objects.add(new StoredObject(
                                object.key().substring(prefix.length()),
                                object.size(),
                                object.lastModified()
                        )));
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);
            return List.copyOf(objects);
        } catch (SdkException exception) {
            throw storageFailure("list", exception);
        }
    }

    private String objectKey(String storageKey) {
        if (storageKey == null
                || storageKey.isBlank()
                || storageKey.contains("/")
                || storageKey.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return prefix + storageKey;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static IOException storageFailure(String operation, Exception cause) {
        return new IOException("Could not " + operation + " object", cause);
    }
}
