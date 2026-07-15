package com.drivingschool.backend.storage;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoredFile {
    private final String storagePath;
    private final String fileName;
    private final Long fileSize;
    private final String contentType;
    private final String fileHash;
}
