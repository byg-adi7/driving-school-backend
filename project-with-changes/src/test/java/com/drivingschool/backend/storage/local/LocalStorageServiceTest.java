package com.drivingschool.backend.storage.local;

import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StorageProperties;
import com.drivingschool.backend.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(new MockEnvironment());
        properties.getLocal().setBasePath(tempDir.toString());
        service = new LocalStorageService(properties, new FileValidator(properties));
    }

    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes();

    @Test
    void store_thenLoad_roundTripsContent() throws Exception {
        byte[] content = VALID_PDF_CONTENT;
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);

        StoredFile stored = service.store(file, "lesson-notes", "42");

        assertThat(stored.getFileName()).isEqualTo("report.pdf");
        assertThat(stored.getFileHash()).isNotBlank();
        assertThat(stored.getStoragePath()).startsWith("lesson-notes/");

        Resource loaded = service.load(stored.getStoragePath());
        assertThat(loaded.getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void store_thenDelete_removesFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", VALID_PDF_CONTENT);
        StoredFile stored = service.store(file, "lesson-notes", "42");

        service.delete(stored.getStoragePath());

        assertThatThrownBy(() -> service.load(stored.getStoragePath())).isInstanceOf(IOException.class);
    }

    @Test
    void load_pathTraversalAttempt_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void load_unknownPath_throwsIOException() {
        assertThatThrownBy(() -> service.load("lesson-notes/2026/01/01/does-not-exist.pdf"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void store_invalidFile_throwsIllegalArgumentExceptionFromValidator() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.store(file, "lesson-notes", "42"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_withRelativeBasePathConfigured_writesUnderAnAbsolutePath() throws Exception {
        // MultipartFile#transferTo(File) resolves a *relative* File against the
        // servlet container's own temp directory, not the JVM's working directory -
        // basePath() must always hand back an absolute path so transferTo() lands in
        // the real configured directory regardless of how app.storage.local.base-path
        // is written (e.g. the default "./uploads").
        String relativeConfigValue = "n1-relative-base-test-dir";
        Path expectedAbsoluteBase = Path.of(relativeConfigValue).toAbsolutePath().normalize();
        try {
            StorageProperties properties = new StorageProperties(new MockEnvironment());
            properties.getLocal().setBasePath(relativeConfigValue);
            LocalStorageService relativeService = new LocalStorageService(properties, new FileValidator(properties));
            MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", VALID_PDF_CONTENT);

            StoredFile stored = relativeService.store(file, "lesson-notes", "42");

            assertThat(expectedAbsoluteBase.resolve(stored.getStoragePath())).exists();
        } finally {
            deleteRecursively(expectedAbsoluteBase);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!java.nio.file.Files.exists(path)) {
            return;
        }
        try (var walk = java.nio.file.Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.delete(p);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
    }
}
