package com.drivingschool.backend.gamification.service;

import com.drivingschool.backend.gamification.dto.GamificationSummaryResponse;
import com.drivingschool.backend.gamification.dto.LeaderboardEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface GamificationService {

    GamificationSummaryResponse getMySummary(Long userId);

    GamificationSummaryResponse getStudentSummary(Long studentId, Long callerUserId, String callerRole);

    Page<LeaderboardEntryResponse> getSchoolLeaderboard(Long schoolId, Pageable pageable, Long callerUserId, String callerRole);

    // Internal hooks - called from Booking/Quiz/DrivingAssessment services, never exposed via controller.
    void awardBookingCompleted(Long studentId, Long bookingId, LocalDateTime completedAt);

    void awardQuizPassed(Long studentId, Long quizId);

    void awardAssessmentPassed(Long studentId, Long assessmentId);
}
