package com.mysend.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MigratingFileStore implements FileStore {

    private final FileStore primary;
    private final FileStore legacy;

    MigratingFileStore(FileStore primary, FileStore legacy) {
        this.primary = primary;
        this.legacy = legacy;
    }

    @Override
    public void put(
            String storageKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) throws IOException {
        primary.put(storageKey, inputStream, contentLength, contentType);
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        try {
            return primary.open(storageKey);
        } catch (FileNotFoundException exception) {
            return legacy.open(storageKey);
        }
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        return primary.exists(storageKey) || legacy.exists(storageKey);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        IOException failure = deleteFrom(primary, storageKey, null);
        failure = deleteFrom(legacy, storageKey, failure);
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public List<StoredObject> list() throws IOException {
        Map<String, StoredObject> objects = new LinkedHashMap<>();
        primary.list().forEach(object -> objects.put(object.storageKey(), object));
        legacy.list().forEach(object -> objects.putIfAbsent(object.storageKey(), object));
        return List.copyOf(objects.values());
    }

    private static IOException deleteFrom(
            FileStore store,
            String storageKey,
            IOException existingFailure
    ) {
        try {
            store.delete(storageKey);
            return existingFailure;
        } catch (IOException exception) {
            if (existingFailure == null) {
                return exception;
            }
            existingFailure.addSuppressed(exception);
            return existingFailure;
        }
    }
}
