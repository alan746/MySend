package com.mysend.file;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface FileStore {

    void put(
            String storageKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) throws IOException;

    InputStream open(String storageKey) throws IOException;

    boolean exists(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    List<StoredObject> list() throws IOException;

    record StoredObject(String storageKey, long sizeBytes, Instant lastModified) {
    }
}
