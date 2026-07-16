package com.drivingschool.backend.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Component
public class FileValidator {

    private static final List<String> DEFAULT_ALLOWED_TYPES = List.of("application/pdf");

    private final StorageProperties properties;

    public FileValidator(StorageProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        // Prevent path traversal in filename
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        long maxBytes = properties.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d MB", properties.getMaxFileSizeMb()));
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedMimeType(contentType)) {
            throw new IllegalArgumentException(
                    String.format("File type '%s' is not allowed", contentType));
        }

        String extension = getFileExtension(filename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException(
                    String.format("File extension '.%s' is not allowed", extension));
        }
    }

    public String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    private boolean isAllowedMimeType(String mimeType) {
        return parseAllowedTypes().stream().anyMatch(type -> mimeType.equalsIgnoreCase(type));
    }

    private boolean isAllowedExtension(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    private List<String> parseAllowedTypes() {
        String allowedMimeTypes = properties.getAllowedMimeTypes();
        if (allowedMimeTypes == null || allowedMimeTypes.isBlank()) {
            return DEFAULT_ALLOWED_TYPES;
        }
        return Arrays.asList(allowedMimeTypes.split(","));
    }
}
