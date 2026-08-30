package com.mysend.file;

import com.mysend.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "mysend.storage.type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(AppProperties properties) throws IOException {
        this.root = properties.uploadDirectory().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void put(
            String storageKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) throws IOException {
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
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(safePath(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(safePath(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(safePath(storageKey));
    }

    @Override
    public List<StoredObject> list() throws IOException {
        try (var paths = Files.list(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return new StoredObject(
                                    path.getFileName().toString(),
                                    Files.size(path),
                                    Files.getLastModifiedTime(path).toInstant()
                            );
                        } catch (IOException exception) {
                            throw new StorageListingException(exception);
                        }
                    })
                    .toList();
        } catch (StorageListingException exception) {
            throw exception.cause;
        }
    }

    private Path safePath(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static final class StorageListingException extends RuntimeException {
        private final IOException cause;

        private StorageListingException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
