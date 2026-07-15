package com.drivingschool.backend.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHasher {

    private FileHasher() {
    }

    public static String sha256Hex(byte[] content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(content);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
