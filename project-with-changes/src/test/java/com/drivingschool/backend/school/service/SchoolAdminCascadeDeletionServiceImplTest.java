package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminCascadeDeletionServiceImplTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;

    private SchoolAdminCascadeDeletionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolAdminCascadeDeletionServiceImpl(schoolRepository, userRepository,
                studentProfileRepository, instructorProfileRepository);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void execute_deletesActiveStudentAndInstructorUsersThenOwningAdmin() {
        User admin = userWithId(1L);
        School school = School.builder().name("X").address("Y").active(true).owningAdmin(admin).build();
        ReflectionTestUtils.setField(school, "id", 10L);

        User studentUser = userWithId(2L);
        StudentProfile student = StudentProfile.builder().user(studentUser).school(school).build();
        User instructorUser = userWithId(3L);
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(school).build();

        when(schoolRepository.findById(10L)).thenReturn(Optional.of(school));
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(10L)).thenReturn(List.of(student));
        when(instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(10L)).thenReturn(List.of(instructor));

        service.execute(10L);

        verify(userRepository).deleteAllByIdInBatch(List.of(2L));
        verify(userRepository).deleteAllByIdInBatch(List.of(3L));
        verify(userRepository).delete(admin);
    }

    @Test
    void execute_leavesAlreadySoftDeletedAccountsUntouchedDirectly() {
        User admin = userWithId(1L);
        School school = School.builder().name("X").address("Y").active(true).owningAdmin(admin).build();
        ReflectionTestUtils.setField(school, "id", 10L);

        when(schoolRepository.findById(10L)).thenReturn(Optional.of(school));
        // A soft-deleted student's row is excluded by the query itself, so it never
        // appears in this list - nothing to explicitly batch-delete for it.
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(10L)).thenReturn(List.of());
        when(instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(10L)).thenReturn(List.of());

        service.execute(10L);

        verify(userRepository, never()).deleteAllByIdInBatch(any());
        verify(userRepository).delete(admin);
    }

    @Test
    void execute_whenOwningAdminIsBootstrap_throwsBadRequestException() {
        User admin = userWithId(1L);
        ReflectionTestUtils.setField(admin, "bootstrapAdmin", true);
        School school = School.builder().name("X").address("Y").active(true).owningAdmin(admin).build();
        ReflectionTestUtils.setField(school, "id", 10L);

        when(schoolRepository.findById(10L)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.execute(10L)).isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void execute_whenSchoolNotFound_throwsResourceNotFoundException() {
        when(schoolRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
