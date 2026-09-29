package com.drivingschool.backend.learning.dto;

import com.drivingschool.backend.learning.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResourceResponse {

    private final Long id;
    private final Long lessonId;
    private final String title;
    // Set for an external link resource; null for an uploaded file.
    private final String fileUrl;
    private final ResourceType type;
    // The rest are set only for an uploaded file. downloadUrl is this API's own
    // endpoint (access-checked on every download), not a direct storage URL.
    private final boolean uploaded;
    private final String fileName;
    private final Long fileSize;
    private final String downloadUrl;
}
