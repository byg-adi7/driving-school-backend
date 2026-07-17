package com.drivingschool.backend.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.drivingschool.backend.storage.FileHasher;
import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StoragePaths;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Cloudinary-backed implementation, active when {@code app.storage.provider=cloudinary}.
 * Files are uploaded as {@code resource_type=raw} (they're PDFs, not images/video) under
 * the {@code type=authenticated} delivery type, so the raw Cloudinary asset URL alone is
 * never enough to fetch it - a signature only this app's API secret can produce is
 * required. This app's own {@code verifyDownloadPermission}-style checks (done by the
 * caller before {@link #load} is ever invoked) remain the real access-control boundary;
 * the authenticated delivery type is defense in depth against the public_id being
 * guessed or leaked outside that path.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private static final String RESOURCE_TYPE = "raw";
    private static final String DELIVERY_TYPE = "authenticated";

    private final Cloudinary cloudinary;
    private final FileValidator validator;
    private final RestTemplate restTemplate;

    public CloudinaryStorageService(Cloudinary cloudinary, FileValidator validator, RestTemplate restTemplate) {
        this.cloudinary = cloudinary;
        this.validator = validator;
        this.restTemplate = restTemplate;
    }

    @Override
    public StoredFile store(MultipartFile file, String folder, String identifier) throws IOException, NoSuchAlgorithmException {
        validator.validate(file);

        String extension = validator.getFileExtension(file.getOriginalFilename());
        String storagePath = StoragePaths.generate(folder, identifier, extension);
        byte[] content = file.getBytes();
        String fileHash = FileHasher.sha256Hex(content);

        try {
            cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", storagePath,
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE));
            log.info("File uploaded successfully to Cloudinary: {} (size: {} bytes)", storagePath, file.getSize());
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", storagePath, e);
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

        String signedUrl = cloudinary.url()
                .resourceType(RESOURCE_TYPE)
                .type(DELIVERY_TYPE)
                .signed(true)
                .generate(storagePath);

        try {
            byte[] content = restTemplate.getForObject(signedUrl, byte[].class);
            if (content == null) {
                throw new IOException("Cloudinary returned no content for: " + storagePath);
            }
            return new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    int lastSlash = storagePath.lastIndexOf('/');
                    return lastSlash == -1 ? storagePath : storagePath.substring(lastSlash + 1);
                }
            };
        } catch (RestClientException e) {
            log.error("Error reading file from Cloudinary: {}", storagePath, e);
            throw new IOException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try {
            cloudinary.uploader().destroy(storagePath, ObjectUtils.asMap(
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE));
            log.info("File deleted successfully from Cloudinary: {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary: {}", storagePath, e);
            throw new IOException("Failed to delete file: " + e.getMessage(), e);
        }
    }
}
