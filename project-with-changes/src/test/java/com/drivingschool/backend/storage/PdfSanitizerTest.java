package com.drivingschool.backend.storage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfSanitizerTest {

    private final PdfSanitizer sanitizer = new PdfSanitizer();

    @Test
    void aPlainPdf_isReturnedByteForByte() {
        byte[] pdf = TestPdfs.blank();

        PdfSanitizer.Result result = sanitizer.sanitize(pdf);

        assertThat(result.changed()).isFalse();
        assertThat(result.content()).isSameAs(pdf);
    }

    @Test
    void aHarmlessOpenAction_likeWordAndLatexExports_isKeptUntouched() {
        byte[] pdf = TestPdfs.withHarmlessOpenAction();

        PdfSanitizer.Result result = sanitizer.sanitize(pdf);

        assertThat(result.changed()).isFalse();
        assertThat(result.content()).isSameAs(pdf);
    }

    @Test
    void aJavaScriptOpenAction_isRemoved_andTheRestKept() throws IOException {
        PdfSanitizer.Result result = sanitizer.sanitize(TestPdfs.withJavaScriptOpenAction());

        assertThat(result.changed()).isTrue();
        try (PDDocument cleaned = Loader.loadPDF(result.content())) {
            assertThat(cleaned.getDocumentCatalog().getOpenAction()).isNull();
            assertThat(cleaned.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void documentLevelJavaScript_isRemoved() throws IOException {
        PdfSanitizer.Result result = sanitizer.sanitize(TestPdfs.withDocumentJavaScript());

        assertThat(result.changed()).isTrue();
        try (PDDocument cleaned = Loader.loadPDF(result.content())) {
            PDDocumentNameDictionary names = cleaned.getDocumentCatalog().getNames();
            assertThat(names == null || names.getJavaScript() == null).isTrue();
        }
    }

    @Test
    void aLaunchLink_losesItsAction_butTheLinkStays() throws IOException {
        PdfSanitizer.Result result = sanitizer.sanitize(TestPdfs.withLaunchLink());

        assertThat(result.changed()).isTrue();
        try (PDDocument cleaned = Loader.loadPDF(result.content())) {
            assertThat(cleaned.getPage(0).getAnnotations()).singleElement()
                    .satisfies(a -> assertThat(((PDAnnotationLink) a).getAction()).isNull());
        }
    }

    @Test
    void embeddedFiles_areRemoved() throws IOException {
        PdfSanitizer.Result result = sanitizer.sanitize(TestPdfs.withEmbeddedFile());

        assertThat(result.changed()).isTrue();
        try (PDDocument cleaned = Loader.loadPDF(result.content())) {
            PDDocumentNameDictionary names = cleaned.getDocumentCatalog().getNames();
            assertThat(names == null || names.getEmbeddedFiles() == null).isTrue();
        }
    }

    @Test
    void aPasswordProtectedPdf_isRejectedWithAClearMessage() {
        assertThatThrownBy(() -> sanitizer.sanitize(TestPdfs.passwordProtected()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password-protected");
    }

    @Test
    void somethingThatOnlyLooksLikeAPdf_isRejected() {
        assertThatThrownBy(() -> sanitizer.sanitize("%PDF-1.4\nnot really\n%%EOF".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be read");
    }
}
