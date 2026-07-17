package com.drivingschool.backend.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;
import com.drivingschool.backend.storage.FileValidator;
import com.drivingschool.backend.storage.StorageProperties;
import com.drivingschool.backend.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryStorageServiceTest {

    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes();
    private static final String SIGNED_URL = "https://res.cloudinary.com/test/raw/authenticated/s--sig--/lesson-notes/x.pdf";

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;
    @Mock private Url url;
    @Mock private RestTemplate restTemplate;

    private CloudinaryStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(new MockEnvironment());
        service = new CloudinaryStorageService(cloudinary, new FileValidator(properties), restTemplate);
    }

    private void stubUrlBuilder() {
        when(cloudinary.url()).thenReturn(url);
        when(url.resourceType(anyString())).thenReturn(url);
        when(url.type(anyString())).thenReturn(url);
        when(url.signed(anyBoolean())).thenReturn(url);
        when(url.generate(anyString())).thenReturn(SIGNED_URL);
    }

    @Test
    void store_uploadsAsRawAuthenticatedAndReturnsMetadata() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", VALID_PDF_CONTENT);

        StoredFile stored = service.store(file, "lesson-notes", "42");

        assertThat(stored.getFileName()).isEqualTo("report.pdf");
        assertThat(stored.getStoragePath()).startsWith("lesson-notes/");
        assertThat(stored.getFileHash()).isNotBlank();
        assertThat(stored.getContentType()).isEqualTo("application/pdf");

        verify(uploader).upload(eq(VALID_PDF_CONTENT), uploadOptionsMatching(stored.getStoragePath()));
    }

    private Map<String, Object> uploadOptionsMatching(String expectedPublicId) {
        return org.mockito.ArgumentMatchers.argThat(options ->
                expectedPublicId.equals(options.get("public_id"))
                        && "raw".equals(options.get("resource_type"))
                        && "authenticated".equals(options.get("type")));
    }

    private Map<String, Object> deleteOptionsMatching() {
        return org.mockito.ArgumentMatchers.argThat(options ->
                "raw".equals(options.get("resource_type"))
                        && "authenticated".equals(options.get("type")));
    }

    @Test
    void store_invalidFile_throwsBeforeCallingCloudinary() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.store(file, "lesson-notes", "42"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cloudinary);
    }

    @Test
    void store_uploadFailure_wrapsAsIOException() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IOException("network blip"));
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", VALID_PDF_CONTENT);

        assertThatThrownBy(() -> service.store(file, "lesson-notes", "42"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to store file");
    }

    @Test
    void load_existingFile_fetchesContentViaSignedUrl() throws Exception {
        stubUrlBuilder();
        byte[] content = "hello world".getBytes();
        when(restTemplate.getForObject(SIGNED_URL, byte[].class)).thenReturn(content);

        Resource resource = service.load("lesson-notes/x.pdf");

        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
        assertThat(resource.getFilename()).isEqualTo("x.pdf");
        verify(url).type("authenticated");
        verify(url).resourceType("raw");
    }

    @Test
    void load_noContentReturned_throwsIOException() {
        stubUrlBuilder();
        when(restTemplate.getForObject(SIGNED_URL, byte[].class)).thenReturn(null);

        assertThatThrownBy(() -> service.load("lesson-notes/x.pdf")).isInstanceOf(IOException.class);
    }

    @Test
    void load_pathTraversalAttempt_throwsIllegalArgumentExceptionWithoutCallingClient() {
        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cloudinary, restTemplate);
    }

    @Test
    void delete_delegatesToUploaderDestroy() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        service.delete("lesson-notes/x.pdf");

        verify(uploader).destroy(eq("lesson-notes/x.pdf"), deleteOptionsMatching());
    }
}
