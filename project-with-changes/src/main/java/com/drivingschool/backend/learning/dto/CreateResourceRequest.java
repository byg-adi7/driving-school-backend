package com.drivingschool.backend.learning.dto;

import com.drivingschool.backend.learning.enums.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateResourceRequest {

    @NotNull(message = "Lesson ID is required")
    private final Long lessonId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private final String title;

    @NotBlank(message = "File URL is required")
    @Size(max = 500)
    private final String fileUrl;

    @NotNull(message = "Resource type is required")
    private final ResourceType type;
}
