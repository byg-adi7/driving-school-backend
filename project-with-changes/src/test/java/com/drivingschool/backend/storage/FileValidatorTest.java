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
        return new FileValidator(propertiesWith(maxFileSizeMb, allowedMimeTypes), new PdfSanitizer());
    }

    private static final byte[] VALID_PDF_CONTENT = com.drivingschool.backend.storage.TestPdfs.blank();

    @Test
    void validate_validPdf_doesNotThrow() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", VALID_PDF_CONTENT);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_contentDoesNotMatchClaimedPdfType_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "MZ this is actually an executable".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match a valid PDF");
    }

    @Test
    void validate_pdfMissingEofMarker_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.4\ntruncated".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end-of-file marker");
    }

    @Test
    void validate_ordinaryExportWithAnOpenAction_isAcceptedUnchanged() throws Exception {
        // Word / Google Docs / LaTeX: "open at page 1, fit width" - used to be rejected.
        byte[] pdf = TestPdfs.withHarmlessOpenAction();
        FileValidator validator = validatorWith(50, "application/pdf");

        byte[] stored = validator.validate(new MockMultipartFile("file", "lecture.pdf", "application/pdf", pdf));

        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo(pdf);
    }

    @Test
    void validate_pdfWithJavaScript_isAccepted_andTheStoredCopyHasItStripped() throws Exception {
        byte[] pdf = TestPdfs.withJavaScriptOpenAction();
        FileValidator validator = validatorWith(50, "application/pdf");

        byte[] stored = validator.validate(new MockMultipartFile("file", "report.pdf", "application/pdf", pdf));

        org.assertj.core.api.Assertions.assertThat(stored).isNotEqualTo(pdf);
        try (var cleaned = org.apache.pdfbox.Loader.loadPDF(stored)) {
            org.assertj.core.api.Assertions.assertThat(cleaned.getDocumentCatalog().getOpenAction()).isNull();
        }
    }

    @Test
    void validate_somethingThatOnlyLooksLikeAPdf_throws() {
        FileValidator validator = validatorWith(50, "application/pdf");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.4\n/Launch (cmd.exe)\n%%EOF".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match a valid PDF");
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

    @Test
    void getFileExtension_nullFilename_returnsEmptyStringRatherThanThrowing() {
        FileValidator validator = validatorWith(50, "application/pdf");

        org.assertj.core.api.Assertions.assertThat(validator.getFileExtension(null)).isEmpty();
    }
}
