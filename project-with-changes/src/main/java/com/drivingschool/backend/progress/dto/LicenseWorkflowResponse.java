package com.drivingschool.backend.progress.dto;

import com.drivingschool.backend.progress.enums.LicenseStage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LicenseWorkflowResponse {

    private final Long id;
    private final Long studentId;
    private final LicenseStage currentStage;
    private final Integer theoryProgressPercent;
    private final Integer roadTrainingHours;
    private final LocalDateTime stageUpdatedAt;
    private final Long approvedByInstructorId;
    private final String notes;
}
