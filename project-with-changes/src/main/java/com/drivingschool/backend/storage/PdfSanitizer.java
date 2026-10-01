package com.drivingschool.backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Removes the parts of an uploaded PDF that can run code or reach outside the document,
 * and keeps everything else - so ordinary files from Word, Google Docs or LaTeX (which
 * routinely carry a harmless "open at page 1 / fit width" /OpenAction) upload fine.
 *
 * Works on the parsed object graph, not the raw bytes: in a modern PDF most objects sit
 * in compressed object streams where a byte search can't see "/JavaScript" at all.
 * A PDF with nothing to remove is returned byte-for-byte unchanged.
 */
@Slf4j
@Component
public class PdfSanitizer {

    /** Action types that run code, launch programs, send/import data or play embedded media. */
    private static final Set<String> DANGEROUS_ACTIONS = Set.of(
            "JavaScript", "Launch", "SubmitForm", "ImportData", "RichMediaExecute", "GoToE", "Rendition");

    /** Opening a web page is fine when the reader clicks a link, not automatically on open. */
    private static final Set<String> DANGEROUS_WHEN_AUTOMATIC = Set.of("URI", "GoToR");

    /** Annotations that embed files, media or 3D content (3D can carry JavaScript). */
    private static final Set<String> DANGEROUS_ANNOTATIONS = Set.of(
            "FileAttachment", "RichMedia", "Screen", "Movie", "Sound", "3D");

    private static final COSName JS = COSName.getPDFName("JS");
    private static final COSName XFA = COSName.getPDFName("XFA");
    private static final COSName EF = COSName.getPDFName("EF");
    private static final COSName COLLECTION = COSName.getPDFName("Collection");
    private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");
    private static final COSName JAVASCRIPT = COSName.getPDFName("JavaScript");
    private static final COSName NEXT = COSName.getPDFName("Next");

    /** The cleaned PDF, and what was removed (empty: the original bytes, untouched). */
    public record Result(byte[] content, List<String> removed) {
        public boolean changed() {
            return !removed.isEmpty();
        }
    }

    public Result sanitize(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            List<String> removed = new ArrayList<>();
            Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            walk(document.getDocument().getTrailer(), removed, visited);
            if (removed.isEmpty()) {
                return new Result(pdf, List.of());
            }
            if (document.isEncrypted()) {
                // Opened with the empty password (permission-only encryption); the saved
                // copy can't be re-encrypted without its owner password.
                document.setAllSecurityToBeRemoved(true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length);
            document.save(out);
            log.info("Sanitized uploaded PDF, removed: {}", removed);
            return new Result(out.toByteArray(), List.copyOf(removed));
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Password-protected PDFs can't be uploaded - save a copy without a password first");
        } catch (IOException e) {
            throw new IllegalArgumentException("File content does not match a valid PDF (it could not be read)");
        }
    }

    private void walk(COSBase node, List<String> removed, Set<COSBase> visited) {
        COSBase base = node instanceof COSObject indirect ? indirect.getObject() : node;
        if (base == null || !visited.add(base)) {
            return;
        }
        if (base instanceof COSArray array) {
            for (int i = 0; i < array.size(); i++) {
                walk(array.get(i), removed, visited);
            }
            return;
        }
        if (!(base instanceof COSDictionary dict)) {
            return;
        }

        clean(dict, removed);
        for (COSName key : new ArrayList<>(dict.keySet())) {
            walk(dict.getItem(key), removed, visited);
        }
    }

    private void clean(COSDictionary dict, List<String> removed) {
        // Scripts attached directly to a dictionary (JavaScript and Rendition actions).
        if (dict.containsKey(JS)) {
            dict.removeItem(JS);
            removed.add("JS");
        }
        // Document-level JavaScript and embedded files live in the /Names tree.
        if (dict.containsKey(JAVASCRIPT) && dict.getDictionaryObject(JAVASCRIPT) instanceof COSDictionary) {
            dict.removeItem(JAVASCRIPT);
            removed.add("JavaScript name tree");
        }
        if (dict.containsKey(EMBEDDED_FILES)) {
            dict.removeItem(EMBEDDED_FILES);
            removed.add("EmbeddedFiles");
        }
        // A file specification's embedded file streams; portfolios; XFA forms (scriptable).
        if (dict.containsKey(EF)) {
            dict.removeItem(EF);
            removed.add("embedded file");
        }
        if (dict.containsKey(COLLECTION)) {
            dict.removeItem(COLLECTION);
            removed.add("Collection");
        }
        if (dict.containsKey(XFA)) {
            dict.removeItem(XFA);
            removed.add("XFA");
        }

        // Automatic actions: on open, and the /AA triggers (page open, focus, keystroke...).
        removeIfDangerous(dict, COSName.OPEN_ACTION, true, removed);
        if (dict.getDictionaryObject(COSName.AA) instanceof COSDictionary additional) {
            for (COSName trigger : new ArrayList<>(additional.keySet())) {
                removeIfDangerous(additional, trigger, true, removed);
            }
            if (additional.size() == 0) {
                dict.removeItem(COSName.AA);
            }
        }
        // Click actions (link annotations, outline items, buttons).
        removeIfDangerous(dict, COSName.A, false, removed);

        if (dict.getDictionaryObject(COSName.ANNOTS) instanceof COSArray annots) {
            for (int i = annots.size() - 1; i >= 0; i--) {
                if (annots.getObject(i) instanceof COSDictionary annot
                        && DANGEROUS_ANNOTATIONS.contains(annot.getNameAsString(COSName.SUBTYPE))) {
                    removed.add(annot.getNameAsString(COSName.SUBTYPE) + " annotation");
                    annots.remove(i);
                }
            }
        }
    }

    private void removeIfDangerous(COSDictionary owner, COSName key, boolean automatic, List<String> removed) {
        if (owner.getDictionaryObject(key) instanceof COSDictionary action && isDangerous(action, automatic)) {
            owner.removeItem(key);
            removed.add(key.getName() + " " + action.getNameAsString(COSName.S));
        }
    }

    // An action is dangerous if it, or anything in its /Next chain, is.
    private boolean isDangerous(COSDictionary action, boolean automatic) {
        return isDangerous(action, automatic, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean isDangerous(COSDictionary action, boolean automatic, Set<COSDictionary> seen) {
        if (!seen.add(action)) {
            return false;
        }
        String type = action.getNameAsString(COSName.S);
        if (type != null && (DANGEROUS_ACTIONS.contains(type) || (automatic && DANGEROUS_WHEN_AUTOMATIC.contains(type)))) {
            return true;
        }
        COSBase next = action.getDictionaryObject(NEXT);
        if (next instanceof COSDictionary nextAction) {
            return isDangerous(nextAction, automatic, seen);
        }
        if (next instanceof COSArray chain) {
            for (int i = 0; i < chain.size(); i++) {
                if (chain.getObject(i) instanceof COSDictionary nextAction && isDangerous(nextAction, automatic, seen)) {
                    return true;
                }
            }
        }
        return false;
    }
}
