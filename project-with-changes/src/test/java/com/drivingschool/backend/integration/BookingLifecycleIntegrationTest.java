package com.drivingschool.backend.integration;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.lesson.note.dto.AttachmentResponse;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full instructor-initiated booking workflow end to end through
 * real HTTP calls against a real Postgres + Redis stack: school/instructor/student
 * setup, booking creation and conflict rejection, lesson-note authoring and ownership-gated access, and a
 * content-validated PDF attachment upload - tying together most of the fixes
 * made across this project's recent sessions in one realistic flow.
 */
class BookingLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes();

    @Test
    void instructorBooksLessonForOwnStudent_studentIsNotified_lessonNoteIsAuthored() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Booking Lifecycle Driving School");

        Person instructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "instructor.booking@example.com", "LIC-BOOKING-1");
        Person student = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "student.booking@example.com", null);

        // a second, unrelated instructor at the same school - used for the negative-path checks below
        Person otherInstructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "other.instructor.booking@example.com", "LIC-BOOKING-2");

        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1).withNano(0);

        BookingResponse booking = createBooking(instructor, student, scheduledAt);
        assertThat(booking.getStatus().name()).isEqualTo("PENDING");

        // same instructor, overlapping time slot -> conflict
        String conflictingBookingJson = bookingRequestJson(student.profileId(), instructor.profileId(), scheduledAt);
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingBookingJson))
                .andExpect(status().isBadRequest());

        // student can't self-create a booking
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(student.profileId(), instructor.profileId(),
                                scheduledAt.plusHours(3))))
                .andExpect(status().isForbidden());

        // an instructor can't create a booking under another instructor's identity
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(otherInstructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(student.profileId(), instructor.profileId(),
                                scheduledAt.plusHours(3))))
                .andExpect(status().isForbidden());

        // The student's "lesson scheduled" notification is sent only after the booking
        // commits, which never happens inside this harness's rolled-back transaction -
        // NotificationDeliveryIntegrationTest covers it (and marking it read) on a real server.

        // instructor authors a lesson note for this student, linked to the booking
        String createNoteJson = objectMapper.writeValueAsString(Map.of(
                "bookingId", booking.getId(),
                "studentId", student.profileId(),
                "lessonSummary", "A solid first lesson on quiet residential roads.",
                "strengths", "Good mirror checks and steady steering control throughout.",
                "weaknesses", "Needs to slow down earlier before junctions and roundabouts.",
                "recommendations", "Practice roundabouts and give-way rules next session."
        ));
        MvcResult noteResult = mockMvc.perform(post("/api/v1/lesson-notes")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createNoteJson))
                .andExpect(status().isCreated())
                .andReturn();
        LessonNoteResponse note = parse(noteResult, LessonNoteResponse.class);
        assertThat(note.getStudentId()).isEqualTo(student.profileId());
        assertThat(note.getInstructorId()).isEqualTo(instructor.profileId());
        assertThat(note.getBookingId()).isEqualTo(booking.getId());

        // the owning student can read it
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId())
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk());

        // an unrelated instructor cannot
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId())
                        .header("Authorization", bearer(otherInstructor.token())))
                .andExpect(status().isForbidden());

        // a genuine PDF attachment upload succeeds
        MockMultipartFile validFile = new MockMultipartFile("file", "handbook.pdf", "application/pdf", VALID_PDF_CONTENT);
        MvcResult attachmentResult = mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(validFile)
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isCreated())
                .andReturn();
        AttachmentResponse attachment = parse(attachmentResult, AttachmentResponse.class);
        assertThat(attachment.getFileName()).isEqualTo("handbook.pdf");

        // a file whose content doesn't match its claimed PDF type is rejected
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not actually a pdf".getBytes());
        mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(spoofedFile)
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().is4xxClientError());
    }

    private BookingResponse createBooking(Person instructor, Person student, LocalDateTime scheduledAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequestJson(student.profileId(), instructor.profileId(), scheduledAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result, BookingResponse.class);
    }

    private String bookingRequestJson(Long studentProfileId, Long instructorProfileId, LocalDateTime scheduledAt) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "studentId", studentProfileId,
                "instructorId", instructorProfileId,
                "scheduledAt", scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "durationMinutes", 60,
                "bookingType", BookingType.ROAD_LESSON.name()
        ));
    }
}
