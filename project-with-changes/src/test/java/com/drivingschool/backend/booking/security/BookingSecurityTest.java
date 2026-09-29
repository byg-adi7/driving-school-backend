package com.drivingschool.backend.booking.security;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSecurityTest {

    private static final Long CURRENT_USER_ID = 100L;

    @Mock private BookingRepository bookingRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private AdminSchoolScope adminSchoolScope;

    private BookingSecurity bookingSecurity;

    @BeforeEach
    void setUp() {
        bookingSecurity = new BookingSecurity(bookingRepository, studentProfileRepository,
                instructorProfileRepository, currentUserService, adminSchoolScope);
        lenient().when(currentUserService.requireUserId()).thenReturn(CURRENT_USER_ID);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Booking bookingBetween(User studentUser, User instructorUser) {
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        Booking booking = Booking.builder()
                .student(student)
                .instructor(instructor)
                .school(School.builder().active(true).build())
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .bookingType(BookingType.ROAD_LESSON)
                .build();
        return booking;
    }

    // --- isParticipant ---

    @Test
    void isParticipant_whenCallerIsTheStudent_returnsTrue() {
        Booking booking = bookingBetween(userWithId(CURRENT_USER_ID), userWithId(999L));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingSecurity.isParticipant(1L)).isTrue();
    }

    @Test
    void isParticipant_whenCallerIsTheInstructor_returnsTrue() {
        Booking booking = bookingBetween(userWithId(999L), userWithId(CURRENT_USER_ID));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingSecurity.isParticipant(1L)).isTrue();
    }

    @Test
    void isParticipant_whenCallerIsUnrelatedUser_returnsFalse() {
        Booking booking = bookingBetween(userWithId(111L), userWithId(222L));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingSecurity.isParticipant(1L)).isFalse();
    }

    @Test
    void isParticipant_whenBookingDoesNotExist_returnsTrueToDeferTo404() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(bookingSecurity.isParticipant(1L)).isTrue();
    }

    // --- isAssignedInstructor ---

    @Test
    void isAssignedInstructor_whenCallerIsAssigned_returnsTrue() {
        Booking booking = bookingBetween(userWithId(999L), userWithId(CURRENT_USER_ID));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingSecurity.isAssignedInstructor(1L)).isTrue();
    }

    @Test
    void isAssignedInstructor_whenCallerIsTheStudentNotInstructor_returnsFalse() {
        Booking booking = bookingBetween(userWithId(CURRENT_USER_ID), userWithId(999L));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingSecurity.isAssignedInstructor(1L)).isFalse();
    }

    // --- isSelfStudent ---

    @Test
    void isSelfStudent_whenStudentProfileBelongsToCaller_returnsTrue() {
        StudentProfile student = StudentProfile.builder().user(userWithId(CURRENT_USER_ID)).school(School.builder().active(true).build()).build();
        when(studentProfileRepository.findById(5L)).thenReturn(Optional.of(student));

        assertThat(bookingSecurity.isSelfStudent(5L)).isTrue();
    }

    @Test
    void isSelfStudent_whenStudentProfileBelongsToSomeoneElse_returnsFalse() {
        StudentProfile student = StudentProfile.builder().user(userWithId(999L)).school(School.builder().active(true).build()).build();
        when(studentProfileRepository.findById(5L)).thenReturn(Optional.of(student));

        assertThat(bookingSecurity.isSelfStudent(5L)).isFalse();
    }

    @Test
    void isSelfStudent_whenStudentIdIsNull_returnsTrueToDeferToValidation() {
        assertThat(bookingSecurity.isSelfStudent(null)).isTrue();
    }

    // --- isSelfInstructor ---

    @Test
    void isSelfInstructor_whenInstructorProfileBelongsToCaller_returnsTrue() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(CURRENT_USER_ID)).active(true).school(School.builder().active(true).build()).build();
        when(instructorProfileRepository.findById(7L)).thenReturn(Optional.of(instructor));

        assertThat(bookingSecurity.isSelfInstructor(7L)).isTrue();
    }

    @Test
    void isSelfInstructor_whenInstructorProfileBelongsToSomeoneElse_returnsFalse() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(999L)).active(true).school(School.builder().active(true).build()).build();
        when(instructorProfileRepository.findById(7L)).thenReturn(Optional.of(instructor));

        assertThat(bookingSecurity.isSelfInstructor(7L)).isFalse();
    }

    // --- hasTaughtStudent ---

    @Test
    void hasTaughtStudent_whenCallerHasBookingWithStudent_returnsTrue() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(CURRENT_USER_ID)).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", 7L);
        when(instructorProfileRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.of(instructor));
        when(bookingRepository.existsByStudent_IdAndInstructor_Id(5L, 7L)).thenReturn(true);

        assertThat(bookingSecurity.hasTaughtStudent(5L)).isTrue();
    }

    @Test
    void hasTaughtStudent_whenCallerHasNoBookingWithStudent_returnsFalse() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(CURRENT_USER_ID)).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", 7L);
        when(instructorProfileRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.of(instructor));
        when(bookingRepository.existsByStudent_IdAndInstructor_Id(5L, 7L)).thenReturn(false);

        assertThat(bookingSecurity.hasTaughtStudent(5L)).isFalse();
    }

    @Test
    void hasTaughtStudent_whenCallerHasNoInstructorProfile_returnsFalse() {
        when(instructorProfileRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.empty());

        assertThat(bookingSecurity.hasTaughtStudent(5L)).isFalse();
    }

    // --- isAdminFor* (regular admin confined to their own school) ---

    @Test
    void isAdminForBooking_delegatesToTheBookingsSchool() {
        Booking booking = bookingBetween(userWithId(1L), userWithId(2L));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(adminSchoolScope.canAccess(booking.getSchool().getId())).thenReturn(false);

        assertThat(bookingSecurity.isAdminForBooking(1L)).isFalse();
    }

    @Test
    void isAdminForBooking_whenBookingDoesNotExist_returnsTrueToDeferTo404() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(bookingSecurity.isAdminForBooking(1L)).isTrue();
    }

    @Test
    void isAdminForStudent_delegatesToTheStudentsSchool() {
        StudentProfile student = StudentProfile.builder().user(userWithId(1L)).school(School.builder().active(true).build()).build();
        when(studentProfileRepository.findById(5L)).thenReturn(Optional.of(student));
        when(adminSchoolScope.canAccess(student.getSchool().getId())).thenReturn(true);

        assertThat(bookingSecurity.isAdminForStudent(5L)).isTrue();
    }

    @Test
    void isAdminForInstructor_delegatesToTheInstructorsSchool() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(2L)).active(true)
                .school(School.builder().active(true).build()).build();
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructor));
        when(adminSchoolScope.canAccess(instructor.getSchool().getId())).thenReturn(false);

        assertThat(bookingSecurity.isAdminForInstructor(2L)).isFalse();
    }
}
