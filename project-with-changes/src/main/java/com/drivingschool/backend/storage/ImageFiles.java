package com.drivingschool.backend.storage;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Recognises an uploaded image by its own bytes - the file name and Content-Type are only claims. */
public final class ImageFiles {

    public enum Type { JPEG, PNG, WEBP, SVG }

    private ImageFiles() {
    }

    /** The image type, or null if the bytes aren't one of the supported formats. */
    public static Type detect(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Type.JPEG;
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (startsWith(b, png)) {
            return Type.PNG;
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Type.WEBP;
        }
        // SVG is text: an <svg> root, optionally after an XML declaration / comments.
        String head = new String(b, 0, Math.min(b.length, 1024), StandardCharsets.UTF_8)
                .replace("\uFEFF", "").stripLeading().toLowerCase(Locale.ROOT);
        if ((head.startsWith("<?xml") || head.startsWith("<svg") || head.startsWith("<!--")) && head.contains("<svg")) {
            return Type.SVG;
        }
        return null;
    }

    private static boolean startsWith(byte[] b, byte[] prefix) {
        if (b.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (b[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
