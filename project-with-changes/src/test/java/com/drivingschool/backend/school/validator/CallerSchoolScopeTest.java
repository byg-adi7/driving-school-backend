package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallerSchoolScopeTest {

    private static final Long CALLER_USER_ID = 1L;

    @Mock private CurrentUserService currentUserService;
    @Mock private AdminSchoolScope adminSchoolScope;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;

    private CallerSchoolScope scope;

    @BeforeEach
    void setUp() {
        scope = new CallerSchoolScope(currentUserService, adminSchoolScope,
                studentProfileRepository, instructorProfileRepository);
    }

    private School school(Long id) {
        School school = School.builder().name("School " + id).active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private void callerIsNonAdmin() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(false);
        when(currentUserService.requireUserId()).thenReturn(CALLER_USER_ID);
    }

    @Test
    void admin_delegatesToAdminSchoolScope() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(adminSchoolScope.restrictedSchoolId()).thenReturn(Optional.of(10L));

        assertThat(scope.callerSchoolId()).contains(10L);
    }

    @Test
    void bootstrapAdmin_isUnrestricted() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(adminSchoolScope.restrictedSchoolId()).thenReturn(Optional.empty());

        assertThat(scope.callerSchoolId()).isEmpty();
        assertThatCode(() -> scope.requireSameSchool(99L)).doesNotThrowAnyException();
    }

    @Test
    void student_isConfinedToTheirProfilesSchool() {
        callerIsNonAdmin();
        when(studentProfileRepository.findByUserId(CALLER_USER_ID))
                .thenReturn(Optional.of(StudentProfile.builder().school(school(10L)).build()));

        assertThatCode(() -> scope.requireSameSchool(10L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireSameSchool(20L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void instructor_isConfinedToTheirProfilesSchool() {
        callerIsNonAdmin();
        when(studentProfileRepository.findByUserId(CALLER_USER_ID)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(CALLER_USER_ID))
                .thenReturn(Optional.of(InstructorProfile.builder().school(school(10L)).build()));

        assertThat(scope.callerSchoolId()).contains(10L);
        assertThatThrownBy(() -> scope.requireSameSchool(20L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonAdminWithNoProfile_isRejected() {
        callerIsNonAdmin();
        when(studentProfileRepository.findByUserId(CALLER_USER_ID)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(CALLER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scope.callerSchoolId())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("do not belong to a school");
    }

    @Test
    void requireSameSchoolAsUser_targetInAnotherSchool_isRejected() {
        callerIsNonAdmin();
        when(studentProfileRepository.findByUserId(CALLER_USER_ID)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(CALLER_USER_ID))
                .thenReturn(Optional.of(InstructorProfile.builder().school(school(10L)).build()));
        when(adminSchoolScope.schoolIdOfUser(50L)).thenReturn(20L);

        assertThatThrownBy(() -> scope.requireSameSchoolAsUser(50L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireSameSchoolAsUser_targetWithNoSchool_isRejectedForNonBootstrapCaller() {
        callerIsNonAdmin();
        when(studentProfileRepository.findByUserId(CALLER_USER_ID))
                .thenReturn(Optional.of(StudentProfile.builder().school(school(10L)).build()));
        when(adminSchoolScope.schoolIdOfUser(50L)).thenReturn(null);

        assertThatThrownBy(() -> scope.requireSameSchoolAsUser(50L)).isInstanceOf(ForbiddenException.class);
    }
}
