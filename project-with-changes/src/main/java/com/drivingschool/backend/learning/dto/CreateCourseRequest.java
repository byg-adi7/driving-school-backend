package com.drivingschool.backend.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateCourseRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private final String title;

    @Size(max = 2000)
    private final String description;

    /**
     * Only honored when the caller is ADMIN - an instructor caller always
     * creates the course under their own profile, regardless of this value.
     */
    private final Long instructorId;
}
