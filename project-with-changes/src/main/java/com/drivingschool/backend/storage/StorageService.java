package com.drivingschool.backend.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Provider-agnostic file storage abstraction. Implementations are selected at
 * runtime via the {@code app.storage.provider} property (see {@link StorageProperties}).
 *
 * Callers should treat the returned/accepted storage path as an opaque token -
 * its internal structure is an implementation detail of whichever provider is active.
 */
public interface StorageService {

    /**
     * Validates and stores a file, organized under the given logical folder
     * (e.g. "lesson-notes") and identifier (e.g. a lesson note ID).
     *
     * @throws IllegalArgumentException if the file fails validation (size, MIME type, filename)
     */
    StoredFile store(MultipartFile file, String folder, String identifier) throws IOException, NoSuchAlgorithmException;

    /**
     * @throws IllegalArgumentException if storagePath is not a valid path previously
     *                                  returned by this service (e.g. path traversal attempt)
     */
    Resource load(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}
