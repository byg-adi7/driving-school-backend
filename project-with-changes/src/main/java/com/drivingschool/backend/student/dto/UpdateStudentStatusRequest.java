package com.drivingschool.backend.student.dto;

import com.drivingschool.backend.student.enums.StudentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateStudentStatusRequest {

    @NotNull(message = "Status is required")
    private final StudentStatus status;
}
