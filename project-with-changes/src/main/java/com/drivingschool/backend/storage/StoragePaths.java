package com.drivingschool.backend.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Shared storage-path layout, so both providers organize files identically: {@code folder/yyyy/MM/dd/identifier_uuid.ext}. */
public final class StoragePaths {

    private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private StoragePaths() {
    }

    public static String generate(String folder, String identifier, String extension) {
        String dateFolder = LocalDate.now().format(DATE_FOLDER);
        String uuid = UUID.randomUUID().toString();
        return String.format("%s/%s/%s_%s.%s", folder, dateFolder, identifier, uuid, extension);
    }
}
