package com.drivingschool.backend.storage.gcs;

import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StorageProperties;
import com.drivingschool.backend.storage.StoredFile;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcsStorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock private Storage storage;
    @Mock private Blob blob;

    private GcsStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(new MockEnvironment());
        properties.getGcs().setBucketName(BUCKET);
        service = new GcsStorageService(storage, properties, new FileValidator(properties, new com.drivingschool.backend.storage.PdfSanitizer()));
    }

    private static final byte[] VALID_PDF_CONTENT = com.drivingschool.backend.storage.TestPdfs.blank();

    @Test
    void store_uploadsToConfiguredBucketAndReturnsMetadata() throws Exception {
        byte[] content = VALID_PDF_CONTENT;
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);

        StoredFile stored = service.store(file, "lesson-notes", "42");

        assertThat(stored.getFileName()).isEqualTo("report.pdf");
        assertThat(stored.getStoragePath()).startsWith("lesson-notes/");
        assertThat(stored.getFileHash()).isNotBlank();
        assertThat(stored.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void store_invalidFile_throwsBeforeCallingStorageClient() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.store(file, "lesson-notes", "42"))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test
    void load_existingBlob_returnsItsContent() throws Exception {
        byte[] content = "hello world".getBytes();
        when(storage.get(BlobId.of(BUCKET, "lesson-notes/x.pdf"))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getContent()).thenReturn(content);

        Resource resource = service.load("lesson-notes/x.pdf");

        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void load_missingBlob_throwsIOException() {
        when(storage.get(BlobId.of(BUCKET, "lesson-notes/missing.pdf"))).thenReturn(null);

        assertThatThrownBy(() -> service.load("lesson-notes/missing.pdf")).isInstanceOf(IOException.class);
    }

    @Test
    void load_pathTraversalAttempt_throwsIllegalArgumentExceptionWithoutCallingClient() {
        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test
    void delete_delegatesToStorageClient() throws Exception {
        when(storage.delete(BlobId.of(BUCKET, "lesson-notes/x.pdf"))).thenReturn(true);

        service.delete("lesson-notes/x.pdf");

        org.mockito.Mockito.verify(storage).delete(BlobId.of(BUCKET, "lesson-notes/x.pdf"));
    }
}
