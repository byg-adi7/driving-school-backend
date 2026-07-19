package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.dto.DrivingAssessmentResponse;
import com.drivingschool.backend.progress.dto.UpdateDrivingAssessmentFeedbackRequest;

import java.util.List;

public interface DrivingAssessmentService {

    DrivingAssessmentResponse createAssessment(CreateDrivingAssessmentRequest request, Long callerId);

    DrivingAssessmentResponse updateFeedback(Long assessmentId, UpdateDrivingAssessmentFeedbackRequest request, Long callerId);

    DrivingAssessmentResponse getAssessment(Long assessmentId, Long userId, String role);

    List<DrivingAssessmentResponse> getStudentAssessments(Long studentId, Long currentUserId, String role);

    List<DrivingAssessmentResponse> getInstructorAssessments(Long instructorId, Long currentUserId, String role);
}
