package com.mysend.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FileStoreTest {

    private S3Client client;
    private S3FileStore store;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        store = new S3FileStore(client, new StorageProperties.S3(
                "https://storage.railway.app",
                "auto",
                "mysend-files-example",
                "access-key",
                "secret-key",
                "virtual",
                "uploads"
        ));
    }

    @Test
    void uploadsIntoTheConfiguredPrivatePrefix() throws Exception {
        store.put(
                "file-1.pdf",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3,
                "application/pdf"
        );

        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("mysend-files-example");
        assertThat(request.getValue().key()).isEqualTo("uploads/file-1.pdf");
        assertThat(request.getValue().contentLength()).isEqualTo(3);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void listsObjectsUsingStorageKeysWithoutThePrivatePrefix() throws Exception {
        Instant modified = Instant.parse("2026-08-30T12:00:00Z");
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder()
                                .key("uploads/file-1.pdf")
                                .size(2048L)
                                .lastModified(modified)
                                .build())
                        .build()
        );

        assertThat(store.list()).containsExactly(
                new FileStore.StoredObject("file-1.pdf", 2048, modified)
        );
    }

    @Test
    void deletesOnlyTheConfiguredObjectKey() throws Exception {
        store.delete("file-1.pdf");

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("uploads/file-1.pdf");
    }
}
