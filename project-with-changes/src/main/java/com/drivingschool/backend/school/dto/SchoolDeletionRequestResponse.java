package com.drivingschool.backend.school.dto;

import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SchoolDeletionRequestResponse {

    private final Long id;
    private final Long schoolId;
    private final String schoolName;
    private final SchoolDeletionRequestStatus status;
    private final String requestedByEmail;
    private final String reviewedByEmail;
    private final String reviewNotes;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime createdAt;
}
