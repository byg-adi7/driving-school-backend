package com.drivingschool.backend.storage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/** Real, parseable PDFs for tests - uploads are now opened with PDFBox, so "%PDF-1.4\n%%EOF" no longer passes. */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] blank() {
        return build(doc -> { });
    }

    /** A different valid PDF (for "replace the file" tests). */
    public static byte[] blank(String title) {
        return build(doc -> {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle(title);
            doc.setDocumentInformation(info);
        });
    }

    /** What Word / Google Docs / LaTeX exports commonly carry: open at page 1, fit width. */
    public static byte[] withHarmlessOpenAction() {
        return build(doc -> {
            PDPageFitWidthDestination destination = new PDPageFitWidthDestination();
            destination.setPage(doc.getPage(0));
            PDActionGoTo goTo = new PDActionGoTo();
            goTo.setDestination(destination);
            doc.getDocumentCatalog().setOpenAction(goTo);
        });
    }

    public static byte[] withJavaScriptOpenAction() {
        return build(doc -> doc.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('x')")));
    }

    public static byte[] withDocumentJavaScript() {
        return build(doc -> {
            PDJavascriptNameTreeNode scripts = new PDJavascriptNameTreeNode();
            scripts.setNames(Map.of("init", new PDActionJavaScript("app.alert('x')")));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
            names.setJavascript(scripts);
            doc.getDocumentCatalog().setNames(names);
        });
    }

    public static byte[] withLaunchLink() {
        return build(doc -> {
            PDAnnotationLink link = new PDAnnotationLink();
            link.setAction(new PDActionLaunch());
            try {
                doc.getPage(0).getAnnotations().add(link);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public static byte[] withEmbeddedFile() {
        return build(doc -> {
            try {
                PDEmbeddedFile embedded = new PDEmbeddedFile(doc, new ByteArrayInputStream("MZ payload".getBytes()));
                PDComplexFileSpecification spec = new PDComplexFileSpecification();
                spec.setFile("payload.exe");
                spec.setEmbeddedFile(embedded);
                PDEmbeddedFilesNameTreeNode files = new PDEmbeddedFilesNameTreeNode();
                files.setNames(Map.of("payload.exe", spec));
                PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
                names.setEmbeddedFiles(files);
                doc.getDocumentCatalog().setNames(names);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public static byte[] passwordProtected() {
        return build(doc -> {
            try {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static byte[] build(Consumer<PDDocument> customize) {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            customize.accept(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
