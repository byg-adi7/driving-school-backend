package com.drivingschool.backend.school.mapper;

import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.entity.SchoolDeletionRequest;
import org.springframework.stereotype.Component;

@Component
public class SchoolDeletionRequestMapper {

    public SchoolDeletionRequestResponse toResponse(SchoolDeletionRequest request) {
        return SchoolDeletionRequestResponse.builder()
                .id(request.getId())
                .schoolId(request.getSchool() != null ? request.getSchool().getId() : null)
                .schoolName(request.getSchoolName())
                .status(request.getStatus())
                .requestedByEmail(request.getRequestedByEmail())
                .reviewedByEmail(request.getReviewedBy() != null ? request.getReviewedBy().getEmail() : null)
                .reviewNotes(request.getReviewNotes())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
