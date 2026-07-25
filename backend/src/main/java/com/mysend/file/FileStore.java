package com.mysend.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface FileStore {

    void put(String storageKey, InputStream inputStream) throws IOException;

    Path resolve(String storageKey);

    void delete(String storageKey) throws IOException;
}
