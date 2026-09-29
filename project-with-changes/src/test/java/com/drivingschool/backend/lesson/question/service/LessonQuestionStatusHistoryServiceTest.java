package com.drivingschool.backend.lesson.question.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionStatusHistoryRepository;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionSubmissionRepository;
import com.drivingschool.backend.lesson.question.validator.QuestionValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonQuestionStatusHistoryServiceTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);
    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    @Mock private LessonQuestionStatusHistoryRepository statusHistoryRepository;
    @Mock private LessonQuestionSubmissionRepository questionRepository;
    private final QuestionValidator validator = new QuestionValidator(adminSchoolScope, callerSchoolScope);

    private LessonQuestionStatusHistoryService service;

    @BeforeEach
    void setUp() {
        service = new LessonQuestionStatusHistoryService(statusHistoryRepository, questionRepository, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LessonQuestionSubmission questionAssignedTo(User instructorUser, User studentUser) {
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        return LessonQuestionSubmission.builder().instructor(instructor).student(student).status(QuestionStatus.PENDING).build();
    }

    @Test
    void getQuestionStatusHistory_asUnrelatedStudent_isDenied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L));
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.getQuestionStatusHistory(10L, 888L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);

        verify(statusHistoryRepository, never()).findByQuestionSubmissionId(10L);
    }

    @Test
    void getQuestionStatusHistory_asOwningStudent_isAllowed() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L));
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));
        when(statusHistoryRepository.findByQuestionSubmissionId(10L)).thenReturn(List.of());

        assertThatCode(() -> service.getQuestionStatusHistory(10L, 2L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void getQuestionStatusHistory_unknownQuestion_throwsResourceNotFoundException() {
        when(questionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuestionStatusHistory(10L, 2L, "STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuestionStatusHistoryPaginated_asUnrelatedInstructor_isDenied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L));
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.getQuestionStatusHistoryPaginated(10L,
                org.springframework.data.domain.Pageable.unpaged(), 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(statusHistoryRepository, never()).findByQuestionSubmissionIdPaginated(anyLong(), any());
    }
}
