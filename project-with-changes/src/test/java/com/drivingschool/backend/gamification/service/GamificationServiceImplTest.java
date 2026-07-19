package com.drivingschool.backend.gamification.service;

import com.drivingschool.backend.common.exception.BadRequestException;
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
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationServiceImplTest {

    @Mock private StudentGameStatsRepository statsRepository;
    @Mock private PointsTransactionRepository pointsTransactionRepository;
    @Mock private BadgeAwardRepository badgeAwardRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    private final GamificationMapper mapper = new GamificationMapper();
    private final GamificationValidator validator = new GamificationValidator();

    private GamificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GamificationServiceImpl(statsRepository, pointsTransactionRepository, badgeAwardRepository,
                studentProfileRepository, instructorProfileRepository, mapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StudentProfile studentProfile(Long profileId, School school) {
        StudentProfile student = StudentProfile.builder().firstName("Sam").lastName("Student")
                .user(userWithId(profileId + 1000)).school(school).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    private School schoolWithId(Long id) {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private void stubNoExistingStats(StudentProfile student) {
        when(studentProfileRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(statsRepository.findByStudent_Id(student.getId())).thenReturn(Optional.empty());
        when(statsRepository.save(any(StudentGameStats.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- awardBookingCompleted ---

    @Test
    void awardBookingCompleted_newEvent_createsLedgerRowAndUpdatesStats() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        stubNoExistingStats(student);
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.BOOKING_COMPLETED, 500L))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.BOOKING_COMPLETED))
                .thenReturn(1L);

        service.awardBookingCompleted(60L, 500L, LocalDateTime.now());

        verify(pointsTransactionRepository).save(any(PointsTransaction.class));
        verify(statsRepository, org.mockito.Mockito.atLeastOnce()).save(any(StudentGameStats.class));
    }

    @Test
    void awardBookingCompleted_duplicateSourceId_isNoOp() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(statsRepository.findByStudent_Id(60L)).thenReturn(Optional.empty());
        when(statsRepository.save(any(StudentGameStats.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.BOOKING_COMPLETED, 500L))
                .thenReturn(true);

        service.awardBookingCompleted(60L, 500L, LocalDateTime.now());

        verify(pointsTransactionRepository, never()).save(any());
    }

    @Test
    void awardBookingCompleted_firstEver_awardsFirstLessonBadge() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        stubNoExistingStats(student);
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.BOOKING_COMPLETED, 500L))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.BOOKING_COMPLETED))
                .thenReturn(1L);
        when(badgeAwardRepository.existsByStudent_IdAndBadge(60L, BadgeType.FIRST_LESSON)).thenReturn(false);

        service.awardBookingCompleted(60L, 500L, LocalDateTime.now());

        verify(badgeAwardRepository).save(argThatBadge(BadgeType.FIRST_LESSON));
    }

    // --- awardQuizPassed ---

    @Test
    void awardQuizPassed_secondAttemptSameQuiz_doesNotAwardPointsAgain() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        StudentGameStats existing = StudentGameStats.builder()
                .student(student).totalPoints(20).currentStreakWeeks(0).longestStreakWeeks(0).build();
        when(statsRepository.findByStudent_Id(60L)).thenReturn(Optional.of(existing));
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.QUIZ_PASSED, 700L))
                .thenReturn(true);

        service.awardQuizPassed(60L, 700L);

        verify(pointsTransactionRepository, never()).save(any());
        verify(statsRepository, never()).save(any());
    }

    @Test
    void awardQuizPassed_firstEver_awardsQuizMasterBadge() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        stubNoExistingStats(student);
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.QUIZ_PASSED, 700L))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.QUIZ_PASSED))
                .thenReturn(1L);

        service.awardQuizPassed(60L, 700L);

        verify(badgeAwardRepository).save(argThatBadge(BadgeType.QUIZ_MASTER));
    }

    // --- awardAssessmentPassed ---

    @Test
    void awardAssessmentPassed_firstEver_awardsRoadReadyBadge() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        stubNoExistingStats(student);
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.ASSESSMENT_PASSED, 800L))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.ASSESSMENT_PASSED))
                .thenReturn(1L);

        service.awardAssessmentPassed(60L, 800L);

        verify(badgeAwardRepository).save(argThatBadge(BadgeType.ROAD_READY));
    }

    // --- streak badges ---

    @Test
    void streakReaching5_awardsFiveWeekStreakBadge() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        StudentGameStats existing = StudentGameStats.builder()
                .student(student).totalPoints(0).currentStreakWeeks(4).longestStreakWeeks(4)
                .lastActivityWeekStart(java.time.LocalDate.of(2026, 6, 1)).build();

        when(statsRepository.findByStudent_Id(60L)).thenReturn(Optional.of(existing));
        when(statsRepository.save(any(StudentGameStats.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(eq(PointsSourceType.BOOKING_COMPLETED), any()))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.BOOKING_COMPLETED))
                .thenReturn(5L);

        service.awardBookingCompleted(60L, 900L, java.time.LocalDate.of(2026, 6, 8).atStartOfDay());

        verify(badgeAwardRepository).save(argThatBadge(BadgeType.FIVE_WEEK_STREAK));
    }

    // --- points threshold badges ---

    @Test
    void totalPointsCrossing100_awardsCenturyClubBadge() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        StudentGameStats existing = StudentGameStats.builder()
                .student(student).totalPoints(95).currentStreakWeeks(0).longestStreakWeeks(0).build();

        when(statsRepository.findByStudent_Id(60L)).thenReturn(Optional.of(existing));
        when(statsRepository.save(any(StudentGameStats.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pointsTransactionRepository.existsBySourceTypeAndSourceId(PointsSourceType.ASSESSMENT_PASSED, 800L))
                .thenReturn(false);
        when(pointsTransactionRepository.countByStudent_IdAndSourceType(60L, PointsSourceType.ASSESSMENT_PASSED))
                .thenReturn(1L);

        service.awardAssessmentPassed(60L, 800L);

        verify(badgeAwardRepository).save(argThatBadge(BadgeType.CENTURY_CLUB));
    }

    // --- access control ---

    @Test
    void getSchoolLeaderboard_asInstructorOfDifferentSchool_throwsBadRequestException() {
        InstructorProfile instructor = InstructorProfile.builder()
                .user(userWithId(1L)).active(true).school(schoolWithId(2L)).build();
        ReflectionTestUtils.setField(instructor, "id", 50L);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));

        assertThatThrownBy(() -> service.getSchoolLeaderboard(1L, Pageable.unpaged(), 1L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getStudentSummary_asAdmin_returnsAnyStudent() {
        StudentProfile student = studentProfile(60L, schoolWithId(1L));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(statsRepository.findByStudent_Id(60L)).thenReturn(Optional.empty());
        when(badgeAwardRepository.findByStudent_IdOrderByCreatedAtAsc(60L)).thenReturn(List.of());
        when(statsRepository.countByStudent_School_IdAndTotalPointsGreaterThan(1L, 0)).thenReturn(0L);

        var response = service.getStudentSummary(60L, 999L, "ADMIN");

        assertThat(response.getStudentId()).isEqualTo(60L);
        assertThat(response.getTotalPoints()).isEqualTo(0);
    }

    private BadgeAward argThatBadge(BadgeType badgeType) {
        return org.mockito.ArgumentMatchers.argThat(award -> award != null && award.getBadge() == badgeType);
    }
}
