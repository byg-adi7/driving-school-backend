package com.drivingschool.backend.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class FileValidator {

    private static final List<String> DEFAULT_ALLOWED_TYPES = List.of("application/pdf");

    // Every type this app accepts today is a PDF, so content validation only
    // needs to know how to recognize one - the client-supplied filename and
    // Content-Type header are just claims and are never trusted on their own.
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String PDF_EOF_MARKER = "%%EOF";

    // Dictionary keys that let a PDF execute code or reach outside itself on open.
    // None of these have a legitimate use in a static lesson-note attachment, so
    // their mere presence is treated as disqualifying rather than trying to judge intent.
    private static final List<String> ACTIVE_CONTENT_MARKERS =
            List.of("/JavaScript", "/JS", "/Launch", "/EmbeddedFile", "/OpenAction", "/AA");

    private final StorageProperties properties;

    public FileValidator(StorageProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) throws IOException {
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

        validateContent(file.getBytes());
    }

    private void validateContent(byte[] content) {
        if (content.length < PDF_SIGNATURE.length || !startsWith(content, PDF_SIGNATURE)) {
            throw new IllegalArgumentException(
                    "File content does not match a valid PDF (the claimed file type does not match its actual content)");
        }

        String text = new String(content, StandardCharsets.ISO_8859_1);
        if (!text.contains(PDF_EOF_MARKER)) {
            throw new IllegalArgumentException("File content does not match a valid PDF (missing end-of-file marker)");
        }

        for (String marker : ACTIVE_CONTENT_MARKERS) {
            if (text.contains(marker)) {
                throw new IllegalArgumentException(
                        "PDF contains active content (" + marker + ") that is not allowed in uploaded documents");
            }
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
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
