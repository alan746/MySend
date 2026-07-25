package com.mysend.file;

import com.mysend.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(AppProperties properties) throws IOException {
        this.root = properties.uploadDirectory().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void put(String storageKey, InputStream inputStream) throws IOException {
        Path destination = safePath(storageKey);
        Path temporary = Files.createTempFile(root, "upload-", ".part");
        try {
            Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        return safePath(storageKey);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(safePath(storageKey));
    }

    private Path safePath(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
