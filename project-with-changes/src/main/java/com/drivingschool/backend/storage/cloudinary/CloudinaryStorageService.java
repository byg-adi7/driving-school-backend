package com.drivingschool.backend.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.drivingschool.backend.common.exception.ServiceUnavailableException;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

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
        byte[] content = validator.validate(file);

        String extension = validator.getFileExtension(file.getOriginalFilename());
        String storagePath = StoragePaths.generate(folder, identifier, extension);
        String fileHash = FileHasher.sha256Hex(content);

        try {
            cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", storagePath,
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE));
            log.info("File uploaded successfully to Cloudinary: {} (size: {} bytes)", storagePath, content.length);
        } catch (IOException | RuntimeException e) {
            // Cloudinary reports its own API errors (bad credentials, "Invalid cloud_name",
            // quota) as plain RuntimeExceptions - not the client's fault, so a 503 with
            // the real reason in the log rather than a generic 500.
            log.error("Failed to upload file to Cloudinary: {} ({})", storagePath, e.getMessage(), e);
            throw new ServiceUnavailableException("File storage is unavailable right now - please try again later", e);
        }

        return StoredFile.builder()
                .storagePath(storagePath)
                .fileName(file.getOriginalFilename())
                .fileSize((long) content.length)
                .contentType(file.getContentType())
                .fileHash(fileHash)
                .build();
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }

        byte[] content = fetch(storagePath);
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
    }

    // First the signed delivery URL (CDN-cached). If Cloudinary refuses it - a new account
    // blocks PDF delivery until "Allow delivery of PDF and ZIP files" is enabled in its
    // security settings - fall back to the signed download API, which serves the original
    // straight from storage. Anything else is a storage outage: 503, not 500.
    private byte[] fetch(String storagePath) {
        String deliveryUrl = cloudinary.url()
                .resourceType(RESOURCE_TYPE)
                .type(DELIVERY_TYPE)
                .signed(true)
                .secure(true)
                .generate(storagePath);
        try {
            return restTemplate.getForObject(deliveryUrl, byte[].class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != 401 && e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) {
                throw unavailable(storagePath, e);
            }
            log.warn("Cloudinary refused delivery of {} ({}); retrying via the download API. If this keeps "
                    + "happening, enable \"Allow delivery of PDF and ZIP files\" in Cloudinary's security settings.",
                    storagePath, e.getStatusCode());
        } catch (RestClientException e) {
            throw unavailable(storagePath, e);
        }

        try {
            String downloadUrl = cloudinary.privateDownload(storagePath, "",
                    Map.of("resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE));
            return restTemplate.getForObject(downloadUrl, byte[].class);
        } catch (Exception e) {
            throw unavailable(storagePath, e);
        }
    }

    private ServiceUnavailableException unavailable(String storagePath, Exception e) {
        log.error("Error reading file from Cloudinary: {} ({})", storagePath, e.getMessage(), e);
        return new ServiceUnavailableException("The file can't be fetched from storage right now - please try again later", e);
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
