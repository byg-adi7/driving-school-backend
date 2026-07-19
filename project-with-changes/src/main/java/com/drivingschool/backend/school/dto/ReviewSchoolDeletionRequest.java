package com.drivingschool.backend.school.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class ReviewSchoolDeletionRequest {

    @Size(max = 1000, message = "Review notes must not exceed 1000 characters")
    private final String reviewNotes;
}
