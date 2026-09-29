package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSchoolScopeTest {

    private static final Long ADMIN_USER_ID = 1L;
    private static final Long OWN_SCHOOL_ID = 10L;
    private static final Long OTHER_SCHOOL_ID = 20L;

    @Mock private CurrentUserService currentUserService;
    @Mock private SchoolRepository schoolRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;

    private AdminSchoolScope scope;

    @BeforeEach
    void setUp() {
        scope = new AdminSchoolScope(currentUserService, schoolRepository,
                studentProfileRepository, instructorProfileRepository);
    }

    private School school(Long id) {
        School school = School.builder().name("School " + id).active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private void callerIsRegularAdminOwning(Long schoolId) {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);
        when(currentUserService.requireUserId()).thenReturn(ADMIN_USER_ID);
        when(schoolRepository.findByOwningAdminId(ADMIN_USER_ID)).thenReturn(Optional.of(school(schoolId)));
    }

    // --- callers this class doesn't restrict ---

    @Test
    void nonAdminCaller_isNeverRestricted_andNoSchoolLookupHappens() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(false);

        assertThat(scope.restrictedSchoolId()).isEmpty();
        assertThat(scope.canAccess(OTHER_SCHOOL_ID)).isTrue();
        assertThatCode(() -> scope.requireAccess(OTHER_SCHOOL_ID)).doesNotThrowAnyException();
        verify(schoolRepository, never()).findByOwningAdminId(any());
    }

    @Test
    void bootstrapAdmin_canAccessEverySchool() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(currentUserService.isBootstrapAdmin()).thenReturn(true);

        assertThat(scope.restrictedSchoolId()).isEmpty();
        assertThat(scope.canAccess(OTHER_SCHOOL_ID)).isTrue();
        verify(schoolRepository, never()).findByOwningAdminId(any());
    }

    // --- regular admin ---

    @Test
    void regularAdmin_isRestrictedToTheirOwnedSchool() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);

        assertThat(scope.restrictedSchoolId()).contains(OWN_SCHOOL_ID);
        assertThat(scope.canAccess(OWN_SCHOOL_ID)).isTrue();
        assertThat(scope.canAccess(OTHER_SCHOOL_ID)).isFalse();
    }

    @Test
    void regularAdmin_requireAccessToAnotherSchool_throwsBadRequest() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);

        assertThatCode(() -> scope.requireAccess(OWN_SCHOOL_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireAccess(OTHER_SCHOOL_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void regularAdmin_nullSchoolId_isNeverAMatch() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);

        assertThat(scope.canAccess(null)).isFalse();
    }

    @Test
    void regularAdminWithNoOwnedSchool_isRejectedRatherThanTreatedAsUnrestricted() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);
        when(currentUserService.requireUserId()).thenReturn(ADMIN_USER_ID);
        when(schoolRepository.findByOwningAdminId(ADMIN_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scope.canAccess(OWN_SCHOOL_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not own a school");
    }

    // --- requireAccessToUser ---

    @Test
    void requireAccessToUser_studentInOwnSchool_isAllowed() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);
        when(studentProfileRepository.findByUserId(50L))
                .thenReturn(Optional.of(StudentProfile.builder().school(school(OWN_SCHOOL_ID)).build()));

        assertThatCode(() -> scope.requireAccessToUser(50L)).doesNotThrowAnyException();
    }

    @Test
    void requireAccessToUser_instructorInAnotherSchool_isRejected() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);
        when(studentProfileRepository.findByUserId(60L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(60L))
                .thenReturn(Optional.of(InstructorProfile.builder().school(school(OTHER_SCHOOL_ID)).build()));

        assertThatThrownBy(() -> scope.requireAccessToUser(60L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requireAccessToUser_anotherSchoolsOwningAdmin_isRejected() {
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);
        when(studentProfileRepository.findByUserId(70L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(70L)).thenReturn(Optional.empty());
        when(schoolRepository.findByOwningAdminId(70L)).thenReturn(Optional.of(school(OTHER_SCHOOL_ID)));

        assertThatThrownBy(() -> scope.requireAccessToUser(70L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requireAccessToUser_userWithNoSchoolAtAll_isRejectedForRegularAdmin() {
        // e.g. the bootstrap admin, who owns no school - only reachable by the bootstrap admin itself
        callerIsRegularAdminOwning(OWN_SCHOOL_ID);
        when(studentProfileRepository.findByUserId(80L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(80L)).thenReturn(Optional.empty());
        when(schoolRepository.findByOwningAdminId(80L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scope.requireAccessToUser(80L))
                .isInstanceOf(BadRequestException.class);
    }
}
