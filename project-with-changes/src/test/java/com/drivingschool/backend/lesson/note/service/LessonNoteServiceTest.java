package com.drivingschool.backend.lesson.note.service;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.lesson.note.repository.LessonNoteRepository;
import com.drivingschool.backend.lesson.note.validator.LessonNoteValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonNoteServiceTest {

    @Mock private LessonNoteRepository lessonNoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private LessonNoteValidator validator;

    private LessonNoteService lessonNoteService;

    @BeforeEach
    void setUp() {
        lessonNoteService = new LessonNoteService(lessonNoteRepository, userRepository,
                instructorProfileRepository, studentProfileRepository, bookingRepository, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StudentProfile studentProfile(Long profileId, User user) {
        StudentProfile student = StudentProfile.builder().user(user).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    private InstructorProfile instructorProfile(Long profileId, User user) {
        InstructorProfile instructor = InstructorProfile.builder().user(user).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    // --- createLessonNote ---

    @Test
    void createLessonNote_withValidRequest_savesNote() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setStudentId(2L);
        request.setLessonSummary("A solid first lesson on quiet roads.");
        request.setStrengths("Good mirror checks and steady steering control.");
        request.setWeaknesses("Needs to slow down earlier before junctions.");
        request.setRecommendations("Practice roundabouts next session.");

        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(lessonNoteRepository.save(any(LessonNote.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = lessonNoteService.createLessonNote(request, 1L);

        assertThat(response.getStudentId()).isEqualTo(2L);
        assertThat(response.getInstructorId()).isEqualTo(1L);
    }

    @Test
    void createLessonNote_whenInstructorProfileMissing_throwsResourceNotFoundException() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setStudentId(2L);
        request.setLessonSummary("A solid first lesson on quiet roads.");
        request.setStrengths("Good mirror checks and steady steering control.");
        request.setWeaknesses("Needs to slow down earlier before junctions.");
        request.setRecommendations("Practice roundabouts next session.");

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonNoteService.createLessonNote(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createLessonNote_withoutBookingId_savesNoteWithNoBooking() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setStudentId(2L);
        request.setLessonSummary("A solid first lesson on quiet roads.");
        request.setStrengths("Good mirror checks and steady steering control.");
        request.setWeaknesses("Needs to slow down earlier before junctions.");
        request.setRecommendations("Practice roundabouts next session.");

        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(lessonNoteRepository.save(any(LessonNote.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = lessonNoteService.createLessonNote(request, 1L);

        assertThat(response.getBookingId()).isNull();
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    void createLessonNote_withBookingBelongingToDifferentInstructor_throwsBadRequestException() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setBookingId(500L);
        request.setStudentId(2L);
        request.setLessonSummary("A solid first lesson on quiet roads.");
        request.setStrengths("Good mirror checks and steady steering control.");
        request.setWeaknesses("Needs to slow down earlier before junctions.");
        request.setRecommendations("Practice roundabouts next session.");

        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        InstructorProfile otherInstructor = instructorProfile(51L, userWithId(9L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        Booking booking = Booking.builder().student(student).instructor(otherInstructor)
                .school(School.builder().active(true).build())
                .scheduledAt(java.time.LocalDateTime.now().plusDays(1))
                .endAt(java.time.LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .status(com.drivingschool.backend.booking.enums.BookingStatus.CONFIRMED)
                .bookingType(com.drivingschool.backend.booking.enums.BookingType.ROAD_LESSON)
                .build();
        ReflectionTestUtils.setField(booking, "id", 500L);

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> lessonNoteService.createLessonNote(request, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(lessonNoteRepository, never()).save(any());
    }

    @Test
    void createLessonNote_withBookingBelongingToInstructorAndStudent_savesNoteWithBooking() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setBookingId(500L);
        request.setStudentId(2L);
        request.setLessonSummary("A solid first lesson on quiet roads.");
        request.setStrengths("Good mirror checks and steady steering control.");
        request.setWeaknesses("Needs to slow down earlier before junctions.");
        request.setRecommendations("Practice roundabouts next session.");

        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        Booking booking = Booking.builder().student(student).instructor(instructor)
                .school(School.builder().active(true).build())
                .scheduledAt(java.time.LocalDateTime.now().plusDays(1))
                .endAt(java.time.LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .status(com.drivingschool.backend.booking.enums.BookingStatus.CONFIRMED)
                .bookingType(com.drivingschool.backend.booking.enums.BookingType.ROAD_LESSON)
                .build();
        ReflectionTestUtils.setField(booking, "id", 500L);

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));
        when(lessonNoteRepository.save(any(LessonNote.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = lessonNoteService.createLessonNote(request, 1L);

        assertThat(response.getBookingId()).isEqualTo(500L);
    }

    // --- getStudentNotes ---

    @Test
    void getStudentNotes_asSelf_returnsNotes() {
        User studentUser = userWithId(2L);
        StudentProfile student = studentProfile(60L, studentUser);
        LessonNote note = LessonNote.builder().instructor(instructorProfile(50L, userWithId(1L))).student(student).build();

        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(lessonNoteRepository.findByStudentId(60L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(note)));

        var result = lessonNoteService.getStudentNotes(2L, Pageable.unpaged(), 2L, "STUDENT");

        assertThat(result.getContent()).hasSize(1);
        verify(validator).validateStudentNotesAccess(student, 2L, "STUDENT", false);
    }

    @Test
    void getStudentNotes_asDifferentStudent_deniedByValidator() {
        User studentUser = userWithId(2L);
        StudentProfile student = studentProfile(60L, studentUser);

        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        org.mockito.Mockito.doThrow(new BadRequestException("denied"))
                .when(validator).validateStudentNotesAccess(student, 999L, "STUDENT", false);

        assertThatThrownBy(() -> lessonNoteService.getStudentNotes(2L, Pageable.unpaged(), 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);

        verify(lessonNoteRepository, never()).findByStudentId(any(), any());
    }

    @Test
    void getStudentNotes_asInstructorWhoHasTaughtStudent_passesTrueFlagToValidator() {
        User studentUser = userWithId(2L);
        StudentProfile student = studentProfile(60L, studentUser);
        InstructorProfile instructor = instructorProfile(50L, userWithId(5L));

        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(instructorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(instructor));
        when(lessonNoteRepository.existsByStudent_IdAndInstructor_Id(60L, 50L)).thenReturn(true);
        when(lessonNoteRepository.findByStudentId(60L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        lessonNoteService.getStudentNotes(2L, Pageable.unpaged(), 5L, "INSTRUCTOR");

        verify(validator).validateStudentNotesAccess(student, 5L, "INSTRUCTOR", true);
    }

    @Test
    void getStudentNotes_unknownStudent_throwsResourceNotFoundException() {
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(studentProfileRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonNoteService.getStudentNotes(2L, Pageable.unpaged(), 999L, "ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getInstructorNotes ---

    @Test
    void getInstructorNotes_asSelf_returnsNotes() {
        User instructorUser = userWithId(1L);
        InstructorProfile instructor = instructorProfile(50L, instructorUser);

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(lessonNoteRepository.findByInstructorId(50L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        lessonNoteService.getInstructorNotes(1L, Pageable.unpaged(), 1L, "INSTRUCTOR");

        verify(validator).validateInstructorNotesAccess(instructor, 1L, "INSTRUCTOR");
    }

    @Test
    void getInstructorNotes_asDifferentInstructor_deniedByValidator() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        org.mockito.Mockito.doThrow(new BadRequestException("denied"))
                .when(validator).validateInstructorNotesAccess(instructor, 999L, "INSTRUCTOR");

        assertThatThrownBy(() -> lessonNoteService.getInstructorNotes(1L, Pageable.unpaged(), 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(lessonNoteRepository, never()).findByInstructorId(any(), any());
    }
}
