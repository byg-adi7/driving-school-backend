package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;

public interface LicenseWorkflowService {

    LicenseWorkflowResponse getByStudentId(Long studentId, Long userId, String role);

    LicenseWorkflowResponse initializeForStudent(Long studentId);

    LicenseWorkflowResponse updateTheoryProgress(Long studentId, int progressPercent, Long userId, String role);

    LicenseWorkflowResponse markQuizPassed(Long studentId);

    LicenseWorkflowResponse advanceStage(Long studentId, AdvanceStageRequest request, Long instructorUserId);
}
