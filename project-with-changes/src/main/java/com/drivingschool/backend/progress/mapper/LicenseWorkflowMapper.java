package com.drivingschool.backend.progress.mapper;

import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import org.springframework.stereotype.Component;

@Component
public class LicenseWorkflowMapper {

    public LicenseWorkflowResponse toResponse(LicenseWorkflow workflow) {
        return LicenseWorkflowResponse.builder()
                .id(workflow.getId())
                .studentId(workflow.getStudent().getId())
                .currentStage(workflow.getCurrentStage())
                .theoryProgressPercent(workflow.getTheoryProgressPercent())
                .roadTrainingHours(workflow.getRoadTrainingHours())
                .stageUpdatedAt(workflow.getStageUpdatedAt())
                .approvedByInstructorId(
                        workflow.getApprovedByInstructor() != null
                                ? workflow.getApprovedByInstructor().getId()
                                : null)
                .notes(workflow.getNotes())
                .build();
    }
}
