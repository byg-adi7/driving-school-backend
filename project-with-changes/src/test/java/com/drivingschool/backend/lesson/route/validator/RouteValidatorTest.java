package com.drivingschool.backend.lesson.route.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
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

class RouteValidatorTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    private final RouteValidator validator = new RouteValidator(adminSchoolScope);

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InstructorProfile instructorFor(User user) {
        return InstructorProfile.builder().user(user).active(true).school(School.builder().active(true).build()).build();
    }

    private PracticalLessonRoute routeFor(User instructorUser) {
        return PracticalLessonRoute.builder().instructor(instructorFor(instructorUser)).build();
    }

    // --- validateReadAccess ---

    @Test
    void validateReadAccess_adminOfSameSchool_allowed() {
        PracticalLessonRoute route = routeFor(userWithId(1L));

        assertThatCode(() -> validator.validateReadAccess(route, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_owningInstructor_allowed() {
        PracticalLessonRoute route = routeFor(userWithId(1L));

        assertThatCode(() -> validator.validateReadAccess(route, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_unrelatedInstructor_denied() {
        PracticalLessonRoute route = routeFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateReadAccess(route, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateOwnership ---

    @Test
    void validateOwnership_owningInstructor_allowed() {
        PracticalLessonRoute route = routeFor(userWithId(1L));

        assertThatCode(() -> validator.validateOwnership(route, 1L)).doesNotThrowAnyException();
    }

    @Test
    void validateOwnership_differentInstructor_denied() {
        PracticalLessonRoute route = routeFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateOwnership(route, 999L))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateInstructorRoutesAccess ---

    @Test
    void validateInstructorRoutesAccess_self_allowed() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatCode(() -> validator.validateInstructorRoutesAccess(instructor, 1L, "INSTRUCTOR"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateInstructorRoutesAccess_differentInstructor_denied() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateInstructorRoutesAccess(instructor, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorRoutesAccess_adminOfSameSchool_allowed() {
        InstructorProfile instructor = instructorFor(userWithId(1L));

        assertThatCode(() -> validator.validateInstructorRoutesAccess(instructor, 999L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_adminOfAnotherSchool_denied() {
        PracticalLessonRoute route = routeFor(userWithId(1L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateReadAccess(route, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorRoutesAccess_adminOfAnotherSchool_denied() {
        InstructorProfile instructor = instructorFor(userWithId(1L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateInstructorRoutesAccess(instructor, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }
}
