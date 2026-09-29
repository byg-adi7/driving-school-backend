package com.drivingschool.backend.live.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.live.entity.LiveSession;
import com.drivingschool.backend.live.enums.SessionStatus;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class LiveSessionValidatorTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    private final LiveSessionValidator validator = new LiveSessionValidator(adminSchoolScope);

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InstructorProfile instructorFor(User user) {
        return InstructorProfile.builder().user(user).active(true).school(School.builder().active(true).build()).build();
    }

    private School schoolWithId(Long id) {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private LiveSession sessionFor(User instructorUser, School school) {
        return LiveSession.builder().instructor(instructorFor(instructorUser)).school(school)
                .status(SessionStatus.SCHEDULED).build();
    }

    // --- validateInstructorSelf ---

    @Test
    void validateInstructorSelf_self_allowed() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatCode(() -> validator.validateInstructorSelf(instructor, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateInstructorSelf_differentInstructor_denied() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateInstructorSelf(instructor, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorSelf_adminOfSameSchool_allowed() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatCode(() -> validator.validateInstructorSelf(instructor, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    // --- validateSchoolAccess ---

    @Test
    void validateSchoolAccess_sameSchool_allowed() {
        assertThatCode(() -> validator.validateSchoolAccess(5L, 5L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void validateSchoolAccess_differentSchool_denied() {
        assertThatThrownBy(() -> validator.validateSchoolAccess(5L, 6L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateSchoolAccess_callerHasNoSchool_denied() {
        assertThatThrownBy(() -> validator.validateSchoolAccess(5L, null, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateSchoolAccess_adminOfSameSchool_allowed() {
        assertThatCode(() -> validator.validateSchoolAccess(5L, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    // --- validateInstructorOwnership ---

    @Test
    void validateInstructorOwnership_owningInstructor_allowed() {
        LiveSession session = sessionFor(userWithId(1L), schoolWithId(5L));

        assertThatCode(() -> validator.validateInstructorOwnership(session, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateInstructorOwnership_differentInstructor_denied() {
        LiveSession session = sessionFor(userWithId(1L), schoolWithId(5L));

        assertThatThrownBy(() -> validator.validateInstructorOwnership(session, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorOwnership_adminOfSameSchool_allowed() {
        LiveSession session = sessionFor(userWithId(1L), schoolWithId(5L));

        assertThatCode(() -> validator.validateInstructorOwnership(session, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    void validateInstructorSelf_adminOfAnotherSchool_denied() {
        InstructorProfile instructor = instructorFor(userWithId(1L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateInstructorSelf(instructor, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateSchoolAccess_adminOfAnotherSchool_denied() {
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateSchoolAccess(5L, null, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorOwnership_adminOfAnotherSchool_denied() {
        LiveSession session = sessionFor(userWithId(1L), schoolWithId(5L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateInstructorOwnership(session, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }
}
