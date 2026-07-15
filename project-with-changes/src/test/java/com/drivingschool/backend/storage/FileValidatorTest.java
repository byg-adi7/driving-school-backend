package com.drivingschool.backend.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    private StorageProperties propertiesWith(long maxFileSizeMb, String allowedMimeTypes) {
        StorageProperties properties = new StorageProperties(new MockEnvironment());
        properties.setMaxFileSizeMb(maxFileSizeMb);
        properties.setAllowedMimeTypes(allowedMimeTypes);
        return properties;
    }

    private FileValidator validatorWith(long maxFileSizeMb, String allowedMimeTypes) {
        return new FileValidator(propertiesWith(maxFileSizeMb, allowedMimeTypes));
    }

    @Test
    void validate_validPdf_doesNotThrow() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_emptyFile_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_pathTraversalInFilename_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_fileTooLarge_throws() {
        FileValidator validator = validatorWith(1, "application/pdf");
        byte[] twoMb = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", twoMb);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void validate_disallowedMimeType_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "report.exe", "application/x-msdownload", "content".getBytes());

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_wrongExtensionEvenWithAllowedMimeType_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void getFileExtension_returnsLowercaseExtension() {
        FileValidator validator = validatorWith(50, "application/pdf");

        org.assertj.core.api.Assertions.assertThat(validator.getFileExtension("Report.PDF")).isEqualTo("pdf");
    }

    @Test
    void getFileExtension_noExtension_returnsEmptyString() {
        FileValidator validator = validatorWith(50, "application/pdf");

        org.assertj.core.api.Assertions.assertThat(validator.getFileExtension("report")).isEmpty();
    }
}
