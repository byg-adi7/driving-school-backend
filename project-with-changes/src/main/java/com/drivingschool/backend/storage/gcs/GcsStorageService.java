package com.drivingschool.backend.storage.gcs;

import com.drivingschool.backend.storage.FileHasher;
import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StorageProperties;
import com.drivingschool.backend.storage.StoragePaths;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.storage.StoredFile;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/** Google Cloud Storage-backed implementation, active when {@code app.storage.provider=gcs}. */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "gcs")
public class GcsStorageService implements StorageService {

    private final Storage storage;
    private final StorageProperties properties;
    private final FileValidator validator;

    public GcsStorageService(Storage storage, StorageProperties properties, FileValidator validator) {
        this.storage = storage;
        this.properties = properties;
        this.validator = validator;
    }

    @Override
    public StoredFile store(MultipartFile file, String folder, String identifier) throws IOException, NoSuchAlgorithmException {
        validator.validate(file);

        String extension = validator.getFileExtension(file.getOriginalFilename());
        String storagePath = StoragePaths.generate(folder, identifier, extension);
        byte[] content = file.getBytes();
        String fileHash = FileHasher.sha256Hex(content);

        BlobId blobId = BlobId.of(bucketName(), storagePath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        try {
            storage.create(blobInfo, content);
            log.info("File uploaded successfully to gs://{}/{} (size: {} bytes)", bucketName(), storagePath, file.getSize());
        } catch (RuntimeException e) {
            log.error("Failed to upload file to gs://{}/{}", bucketName(), storagePath, e);
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

        Blob blob = storage.get(BlobId.of(bucketName(), storagePath));
        if (blob == null || !blob.exists()) {
            log.warn("File not found: gs://{}/{}", bucketName(), storagePath);
            throw new IOException("File not found: " + storagePath);
        }

        try {
            byte[] content = blob.getContent();
            return new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    int lastSlash = storagePath.lastIndexOf('/');
                    return lastSlash == -1 ? storagePath : storagePath.substring(lastSlash + 1);
                }
            };
        } catch (RuntimeException e) {
            log.error("Error reading file: gs://{}/{}", bucketName(), storagePath, e);
            throw new IOException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try {
            boolean deleted = storage.delete(BlobId.of(bucketName(), storagePath));
            if (deleted) {
                log.info("File deleted successfully: gs://{}/{}", bucketName(), storagePath);
            }
        } catch (RuntimeException e) {
            log.error("Failed to delete file: gs://{}/{}", bucketName(), storagePath, e);
            throw new IOException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    private String bucketName() {
        return properties.getGcs().getBucketName();
    }
}
