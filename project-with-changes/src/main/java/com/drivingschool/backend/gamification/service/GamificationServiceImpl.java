package com.drivingschool.backend.gamification.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.gamification.dto.GamificationSummaryResponse;
import com.drivingschool.backend.gamification.dto.LeaderboardEntryResponse;
import com.drivingschool.backend.gamification.entity.BadgeAward;
import com.drivingschool.backend.gamification.entity.PointsTransaction;
import com.drivingschool.backend.gamification.entity.StudentGameStats;
import com.drivingschool.backend.gamification.enums.BadgeType;
import com.drivingschool.backend.gamification.enums.PointsSourceType;
import com.drivingschool.backend.gamification.mapper.GamificationMapper;
import com.drivingschool.backend.gamification.repository.BadgeAwardRepository;
import com.drivingschool.backend.gamification.repository.PointsTransactionRepository;
import com.drivingschool.backend.gamification.repository.StudentGameStatsRepository;
import com.drivingschool.backend.gamification.validator.GamificationValidator;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Service
public class GamificationServiceImpl implements GamificationService {

    private static final int POINTS_BOOKING_COMPLETED = 10;
    private static final int POINTS_QUIZ_PASSED = 20;
    private static final int POINTS_ASSESSMENT_PASSED = 30;

    private final StudentGameStatsRepository statsRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final BadgeAwardRepository badgeAwardRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final GamificationMapper mapper;
    private final GamificationValidator validator;

    public GamificationServiceImpl(StudentGameStatsRepository statsRepository,
                                   PointsTransactionRepository pointsTransactionRepository,
                                   BadgeAwardRepository badgeAwardRepository,
                                   StudentProfileRepository studentProfileRepository,
                                   InstructorProfileRepository instructorProfileRepository,
                                   GamificationMapper mapper,
                                   GamificationValidator validator) {
        this.statsRepository = statsRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.badgeAwardRepository = badgeAwardRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    @Transactional(readOnly = true)
    public GamificationSummaryResponse getMySummary(Long userId) {
        StudentProfile student = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + userId));
        return buildSummary(student);
    }

    @Override
    @Transactional(readOnly = true)
    public GamificationSummaryResponse getStudentSummary(Long studentId, Long callerUserId, String callerRole) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", studentId));
        Long callerSchoolId = resolveCallerSchoolId(callerUserId, callerRole);
        validator.validateStudentSummaryAccess(student, callerUserId, callerRole, callerSchoolId);
        return buildSummary(student);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaderboardEntryResponse> getSchoolLeaderboard(Long schoolId, Pageable pageable,
                                                               Long callerUserId, String callerRole) {
        Long callerSchoolId = resolveCallerSchoolId(callerUserId, callerRole);
        validator.validateLeaderboardAccess(schoolId, callerRole, callerSchoolId);

        Page<StudentGameStats> page = statsRepository.findBySchoolIdOrderByTotalPointsDesc(schoolId, pageable);
        long offset = pageable.getOffset();
        List<StudentGameStats> content = page.getContent();
        return page.map(stats -> mapper.toLeaderboardEntry(stats, (int) (offset + content.indexOf(stats) + 1)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardBookingCompleted(Long studentId, Long bookingId, LocalDateTime completedAt) {
        StudentGameStats stats = getOrCreateStats(studentId);
        boolean firstTime = awardPointsIfNew(stats, PointsSourceType.BOOKING_COMPLETED, bookingId,
                POINTS_BOOKING_COMPLETED, "Completed lesson #" + bookingId);
        if (firstTime) {
            LocalDate weekStart = completedAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            stats.applyQualifyingWeek(weekStart);
            statsRepository.save(stats);
            checkStreakBadges(stats);
            if (pointsTransactionRepository.countByStudent_IdAndSourceType(studentId, PointsSourceType.BOOKING_COMPLETED) == 1) {
                awardBadgeIfNew(stats.getStudent(), BadgeType.FIRST_LESSON);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardQuizPassed(Long studentId, Long quizId) {
        StudentGameStats stats = getOrCreateStats(studentId);
        boolean firstTime = awardPointsIfNew(stats, PointsSourceType.QUIZ_PASSED, quizId,
                POINTS_QUIZ_PASSED, "Passed quiz #" + quizId);
        if (firstTime && pointsTransactionRepository.countByStudent_IdAndSourceType(studentId, PointsSourceType.QUIZ_PASSED) == 1) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.QUIZ_MASTER);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardAssessmentPassed(Long studentId, Long assessmentId) {
        StudentGameStats stats = getOrCreateStats(studentId);
        boolean firstTime = awardPointsIfNew(stats, PointsSourceType.ASSESSMENT_PASSED, assessmentId,
                POINTS_ASSESSMENT_PASSED, "Passed driving assessment #" + assessmentId);
        if (firstTime && pointsTransactionRepository.countByStudent_IdAndSourceType(studentId, PointsSourceType.ASSESSMENT_PASSED) == 1) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.ROAD_READY);
        }
    }

    private boolean awardPointsIfNew(StudentGameStats stats, PointsSourceType sourceType, Long sourceId,
                                     int points, String description) {
        if (pointsTransactionRepository.existsBySourceTypeAndSourceId(sourceType, sourceId)) {
            return false;
        }
        try {
            pointsTransactionRepository.save(PointsTransaction.builder()
                    .student(stats.getStudent()).points(points).sourceType(sourceType)
                    .sourceId(sourceId).description(description).build());
        } catch (DataIntegrityViolationException dup) {
            log.info("Points already awarded (race detected): sourceType={}, sourceId={}", sourceType, sourceId);
            return false;
        }
        stats.addPoints(points);
        statsRepository.save(stats);
        checkPointsThresholdBadges(stats);
        return true;
    }

    private void checkPointsThresholdBadges(StudentGameStats stats) {
        if (stats.getTotalPoints() >= 100) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.CENTURY_CLUB);
        }
        if (stats.getTotalPoints() >= 500) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.HIGH_ACHIEVER);
        }
    }

    private void checkStreakBadges(StudentGameStats stats) {
        if (stats.getCurrentStreakWeeks() >= 5) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.FIVE_WEEK_STREAK);
        }
        if (stats.getCurrentStreakWeeks() >= 10) {
            awardBadgeIfNew(stats.getStudent(), BadgeType.TEN_WEEK_STREAK);
        }
    }

    private void awardBadgeIfNew(StudentProfile student, BadgeType badge) {
        if (badgeAwardRepository.existsByStudent_IdAndBadge(student.getId(), badge)) {
            return;
        }
        try {
            badgeAwardRepository.save(BadgeAward.builder().student(student).badge(badge).build());
        } catch (DataIntegrityViolationException dup) {
            log.info("Badge already awarded (race detected): studentId={}, badge={}", student.getId(), badge);
        }
    }

    private StudentGameStats getOrCreateStats(Long studentId) {
        return statsRepository.findByStudent_Id(studentId).orElseGet(() -> {
            StudentProfile student = studentProfileRepository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", studentId));
            return statsRepository.save(StudentGameStats.builder()
                    .student(student).totalPoints(0).currentStreakWeeks(0).longestStreakWeeks(0).build());
        });
    }

    private GamificationSummaryResponse buildSummary(StudentProfile student) {
        StudentGameStats stats = statsRepository.findByStudent_Id(student.getId())
                .orElseGet(() -> StudentGameStats.builder()
                        .student(student).totalPoints(0).currentStreakWeeks(0).longestStreakWeeks(0).build());
        List<BadgeAward> badges = badgeAwardRepository.findByStudent_IdOrderByCreatedAtAsc(student.getId());
        int rank = (int) statsRepository.countByStudent_School_IdAndTotalPointsGreaterThan(
                student.getSchool().getId(), stats.getTotalPoints()) + 1;
        return mapper.toSummaryResponse(stats, badges, rank);
    }

    private Long resolveCallerSchoolId(Long callerUserId, String callerRole) {
        if ("INSTRUCTOR".equals(callerRole)) {
            return instructorProfileRepository.findByUserId(callerUserId)
                    .map(InstructorProfile::getSchool)
                    .map(school -> school.getId())
                    .orElse(null);
        }
        if ("STUDENT".equals(callerRole)) {
            return studentProfileRepository.findByUserId(callerUserId)
                    .map(StudentProfile::getSchool)
                    .map(school -> school.getId())
                    .orElse(null);
        }
        return null;
    }
}
