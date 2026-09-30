package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import com.drivingschool.backend.progress.enums.LicenseStage;
import com.drivingschool.backend.progress.mapper.LicenseWorkflowMapper;
import com.drivingschool.backend.progress.repository.LicenseWorkflowRepository;
import com.drivingschool.backend.progress.validator.LicenseWorkflowValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseWorkflowServiceImplTest {

    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    @Mock private LicenseWorkflowRepository licenseWorkflowRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    private final LicenseWorkflowMapper licenseWorkflowMapper = new LicenseWorkflowMapper();
    private final LicenseWorkflowValidator validator = new LicenseWorkflowValidator(callerSchoolScope);

    private LicenseWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LicenseWorkflowServiceImpl(licenseWorkflowRepository, studentProfileRepository,
                instructorProfileRepository, licenseWorkflowMapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LicenseWorkflow workflowFor(User studentUser) {
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(student, "id", 20L);
        return LicenseWorkflow.builder()
                .student(student)
                .currentStage(LicenseStage.THEORY_LEARNING)
                .theoryProgressPercent(0)
                .roadTrainingHours(0)
                .stageUpdatedAt(LocalDateTime.now())
                .build();
    }

    // --- getByStudentId ---

    @Test
    void getByStudentId_asOwningStudent_isAllowed() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));

        assertThatCode(() -> service.getByStudentId(20L, 1L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void getByStudentId_asDifferentStudent_isDenied() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> service.getByStudentId(20L, 999L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getByStudentId_asUnrelatedInstructor_isAllowed() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));

        assertThatCode(() -> service.getByStudentId(20L, 999L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    // --- updateTheoryProgress ---

    @Test
    void updateTheoryProgress_asDifferentStudent_isDenied() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> service.updateTheoryProgress(20L, 50, 999L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);

        verify(licenseWorkflowRepository, never()).save(any());
    }

    @Test
    void updateTheoryProgress_asOwningStudent_isAllowedAndPersisted() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));
        when(licenseWorkflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateTheoryProgress(20L, 50, 1L, "STUDENT");

        verify(licenseWorkflowRepository).save(workflow);
    }

    // --- markQuizPassed: no STUDENT restriction to test here, since the controller
    // now gates this endpoint to ADMIN only. The service method itself is intentionally
    // still callable with any studentId, because QuizServiceImpl.submit() invokes it
    // directly (in-process, not through the controller) after it has already resolved
    // and verified the correct student. No change needed/tested at the service level.

    @Test
    void initializeForStudent_asCallerOfAnotherSchool_isDeniedBeforeRevealingWorkflowState() {
        LicenseWorkflow existing = workflowFor(userWithId(1L));
        when(studentProfileRepository.findById(20L)).thenReturn(Optional.of(existing.getStudent()));
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> service.initializeForStudent(20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("no access");
        verify(licenseWorkflowRepository, never()).existsByStudentId(any());
        verify(licenseWorkflowRepository, never()).save(any());
    }

    @Test
    void markQuizPassed_asCallerOfAnotherSchool_isDenied() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> service.markQuizPassed(20L))
                .isInstanceOf(BadRequestException.class);
        verify(licenseWorkflowRepository, never()).save(any());
    }

    @Test
    void advanceStage_asInstructorOfAnotherSchool_isDenied() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));
        when(licenseWorkflowRepository.findByStudentId(20L)).thenReturn(Optional.of(workflow));
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());
        AdvanceStageRequest request = AdvanceStageRequest.builder().targetStage(LicenseStage.THEORY_COMPLETED).build();

        assertThatThrownBy(() -> service.advanceStage(20L, request, 5L))
                .isInstanceOf(BadRequestException.class);
        verify(licenseWorkflowRepository, never()).save(any());
    }
}
