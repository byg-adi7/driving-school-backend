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
    private final String fileUrl;
    private final ResourceType type;
}
