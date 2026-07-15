package com.drivingschool.backend.storage.local;

import com.drivingschool.backend.storage.FileHasher;
import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StorageProperties;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.storage.StoragePaths;
import com.drivingschool.backend.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

/**
 * Local-disk storage. Active by default (dev/test) and whenever
 * {@code app.storage.provider=local}. Not suitable as the sole storage backend
 * in a multi-instance production deployment - use gcs there.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;
    private final FileValidator validator;

    public LocalStorageService(StorageProperties properties, FileValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    @Override
    public StoredFile store(MultipartFile file, String folder, String identifier) throws IOException, NoSuchAlgorithmException {
        validator.validate(file);

        String extension = validator.getFileExtension(file.getOriginalFilename());
        String storagePath = StoragePaths.generate(folder, identifier, extension);
        String fileHash = FileHasher.sha256Hex(file.getBytes());

        Path basePath = Paths.get(properties.getLocal().getBasePath());
        Path filePath = basePath.resolve(storagePath);
        Files.createDirectories(filePath.getParent());

        try {
            file.transferTo(filePath.toFile());
            log.info("File uploaded successfully: {} (size: {} bytes)", storagePath, file.getSize());
        } catch (IOException e) {
            log.error("Failed to upload file: {}", storagePath, e);
            throw new IOException("Failed to store file: " + e.getMessage(), e);
        }

        return StoredFile.builder()
                .storagePath(storagePath)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .fileHash(fileHash)
                .build();
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Path basePath = Paths.get(properties.getLocal().getBasePath());
        Path filePath = basePath.resolve(storagePath);

        if (!Files.exists(filePath)) {
            log.warn("File not found: {}", storagePath);
            throw new IOException("File not found: " + storagePath);
        }

        if (!filePath.toRealPath().startsWith(basePath.toRealPath())) {
            log.warn("Attempted path traversal: {}", storagePath);
            throw new IllegalArgumentException("Invalid file path");
        }

        try {
            return new UrlResource(filePath.toUri());
        } catch (IOException e) {
            log.error("Error reading file: {}", storagePath, e);
            throw new IOException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Path basePath = Paths.get(properties.getLocal().getBasePath());
        Path filePath = basePath.resolve(storagePath);

        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted successfully: {}", storagePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", storagePath, e);
            throw new IOException("Failed to delete file: " + e.getMessage(), e);
        }
    }
}
