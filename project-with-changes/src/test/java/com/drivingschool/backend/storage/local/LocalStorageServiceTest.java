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

    @Test
    void store_thenLoad_roundTripsContent() throws Exception {
        byte[] content = "hello world".getBytes();
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
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());
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
}
