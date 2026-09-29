package com.drivingschool.backend.integration;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.student.enums.StudentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A regular (non-bootstrap) admin is confined to the one school they own, across
 * every module - not just the school module, which was the only place ownership
 * was enforced when the school/admin ownership model first landed. Two real
 * schools, each with its own admin, against a real Postgres + Redis stack:
 * school A's admin must not be able to read or change anything of school B's by
 * passing B's IDs, while B's own admin and the bootstrap admin still can.
 */
class AdminSchoolScopingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    private SchoolWithAdminResponse createSchoolWithAdmin(String bootstrapToken, String schoolName, String adminEmail) throws Exception {
        CreateSchoolWithAdminRequest request = CreateSchoolWithAdminRequest.builder()
                .schoolName(schoolName)
                .schoolAddress("1 Test Street")
                .adminEmail(adminEmail)
                .adminPassword(PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", bearer(bootstrapToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return parse(result, SchoolWithAdminResponse.class);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private List<Integer> pageContentIds(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> page = (Map<String, Object>) body.get("data");
        return ((List<Map<String, Object>>) page.get("content")).stream()
                .map(row -> ((Number) row.get("id")).intValue())
                .toList();
    }

    @Test
    void regularAdmin_cannotReadOrChangeAnotherSchoolsRecords() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        Long schoolAId = createSchoolWithAdmin(bootstrapToken, "Scope School A", "owner.scope.a@example.com")
                .getSchool().getId();
        Long schoolBId = createSchoolWithAdmin(bootstrapToken, "Scope School B", "owner.scope.b@example.com")
                .getSchool().getId();
        String adminAToken = login("owner.scope.a@example.com", PASSWORD);
        String adminBToken = login("owner.scope.b@example.com", PASSWORD);

        // School B's own admin can still populate school B.
        Person studentB = registerAndIdentify(adminBToken, schoolBId, RoleName.STUDENT, "student.scope.b@example.com", null);
        Person instructorB = registerAndIdentify(adminBToken, schoolBId, RoleName.INSTRUCTOR, "instructor.scope.b@example.com", "LIC-SCOPE-B");

        // --- accounts ---

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "intruder.student@example.com",
                                "password", PASSWORD,
                                "firstName", "Intruder",
                                "lastName", "Student",
                                "schoolId", schoolBId,
                                "role", RoleName.STUDENT.name()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/users/" + studentB.userId()).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isBadRequest());
        // ...and the attempted delete really didn't land: B's student can still use their account.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(studentB.token())))
                .andExpect(status().isOk());

        // --- student/instructor profiles ---

        mockMvc.perform(get("/api/v1/students/school/" + schoolBId).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/instructors/school/" + schoolBId).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/students/" + studentB.profileId() + "/status")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", StudentStatus.SUSPENDED.name()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/instructors/" + instructorB.profileId() + "/active")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("active", false))))
                .andExpect(status().isBadRequest());

        // Admin A still has full access to their own school.
        mockMvc.perform(get("/api/v1/students/school/" + schoolAId).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isOk());

        // --- vehicles ---

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "registrationNumber", "SCOPE-B-1",
                                "make", "Toyota",
                                "model", "Corolla",
                                "modelYear", 2022,
                                "color", "White",
                                "schoolId", schoolBId))))
                .andExpect(status().isBadRequest());

        // --- bookings ---

        String bookingJson = json(Map.of(
                "studentId", studentB.profileId(),
                "instructorId", instructorB.profileId(),
                "scheduledAt", LocalDateTime.now().plusDays(2).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "durationMinutes", 60,
                "bookingType", BookingType.ROAD_LESSON.name()));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson))
                .andExpect(status().isForbidden());

        MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(adminBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson))
                .andExpect(status().isCreated())
                .andReturn();
        Long bookingId = parse(bookingResult, BookingResponse.class).getId();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/bookings/" + bookingId + "/cancel").header("Authorization", bearer(adminAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/bookings/student/" + studentB.profileId()).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isForbidden());

        // --- notifications ---

        mockMvc.perform(post("/api/v1/notifications/send")
                        .header("Authorization", bearer(adminAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "userId", studentB.userId(),
                                "subject", "Not your student",
                                "body", "This should never be delivered.",
                                "channel", NotificationChannel.IN_APP.name()))))
                .andExpect(status().isBadRequest());

        // --- ADMIN-only "list everything" endpoints are filtered, not rejected ---

        MvcResult noteResult = mockMvc.perform(post("/api/v1/lesson-notes")
                        .header("Authorization", bearer(instructorB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentB.profileId(),
                                "lessonSummary", "Parking practice in the school car park.",
                                "strengths", "Careful, well-controlled clutch work throughout.",
                                "weaknesses", "Loses reference points when reversing into a bay.",
                                "recommendations", "Repeat bay parking with a focus on mirror checks."))))
                .andExpect(status().isCreated())
                .andReturn();
        int noteId = parse(noteResult, LessonNoteResponse.class).getId().intValue();

        MvcResult adminANotes = mockMvc.perform(get("/api/v1/lesson-notes").header("Authorization", bearer(adminAToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pageContentIds(adminANotes)).doesNotContain(noteId);

        MvcResult adminBNotes = mockMvc.perform(get("/api/v1/lesson-notes").header("Authorization", bearer(adminBToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pageContentIds(adminBNotes)).contains(noteId);

        mockMvc.perform(get("/api/v1/lesson-notes/" + noteId).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isBadRequest());

        // --- the bootstrap admin is still unrestricted ---

        mockMvc.perform(get("/api/v1/students/school/" + schoolBId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());
        MvcResult bootstrapNotes = mockMvc.perform(get("/api/v1/lesson-notes").header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pageContentIds(bootstrapNotes)).contains(noteId);
    }

    @Test
    void instructorsAndStudents_cannotReachAnotherSchoolsRecords() throws Exception {
        // Everyone is registered through the bootstrap admin to stay inside the
        // 10-per-minute auth-endpoint rate limit (register + login count against it).
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        Long schoolAId = createSchoolWithAdmin(bootstrapToken, "Staff Scope School A", "owner.staff.a@example.com")
                .getSchool().getId();
        Long schoolBId = createSchoolWithAdmin(bootstrapToken, "Staff Scope School B", "owner.staff.b@example.com")
                .getSchool().getId();

        Person instructorA = registerAndIdentify(bootstrapToken, schoolAId, RoleName.INSTRUCTOR, "instructor.staff.a@example.com", "LIC-STAFF-A");
        Person studentA = registerAndIdentify(bootstrapToken, schoolAId, RoleName.STUDENT, "student.staff.a@example.com", null);
        Person instructorB = registerAndIdentify(bootstrapToken, schoolBId, RoleName.INSTRUCTOR, "instructor.staff.b@example.com", "LIC-STAFF-B");
        Person studentB = registerAndIdentify(bootstrapToken, schoolBId, RoleName.STUDENT, "student.staff.b@example.com", null);

        // An instructor can't write a lesson note for another school's student (which
        // would also have unlocked reading that student's whole note history)...
        mockMvc.perform(post("/api/v1/lesson-notes")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentB.profileId(),
                                "lessonSummary", "Parking practice in the school car park.",
                                "strengths", "Careful, well-controlled clutch work throughout.",
                                "weaknesses", "Loses reference points when reversing into a bay.",
                                "recommendations", "Repeat bay parking with a focus on mirror checks."))))
                .andExpect(status().isBadRequest());
        // ...nor record a driving assessment for one.
        mockMvc.perform(post("/api/v1/driving-assessments")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentB.profileId(),
                                "assessmentDate", LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "score", 90,
                                "result", "PASSED"))))
                .andExpect(status().isBadRequest());

        // An instructor can't schedule a live session under another school.
        mockMvc.perform(post("/api/v1/live-sessions")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "instructorId", instructorA.profileId(),
                                "schoolId", schoolBId,
                                "title", "Not my school",
                                "scheduledAt", LocalDateTime.now().plusDays(3).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "durationMinutes", 60,
                                "meetingUrl", "https://example.com/meet"))))
                .andExpect(status().isBadRequest());

        // An instructor can't message another school's user, or read its vehicles.
        mockMvc.perform(post("/api/v1/notifications/send")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "userId", studentB.userId(),
                                "subject", "Not your student",
                                "body", "This should never be delivered.",
                                "channel", NotificationChannel.IN_APP.name()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/vehicles/school/" + schoolBId).header("Authorization", bearer(instructorA.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/vehicles/school/" + schoolAId).header("Authorization", bearer(instructorA.token())))
                .andExpect(status().isOk());

        // An instructor can't read another school's student's license workflow.
        mockMvc.perform(post("/api/v1/progress/license/students/" + studentB.profileId() + "/initialize")
                        .header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/progress/license/students/" + studentB.profileId())
                        .header("Authorization", bearer(instructorA.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/progress/license/students/" + studentB.profileId())
                        .header("Authorization", bearer(instructorB.token())))
                .andExpect(status().isOk());

        // A student can't assign a question to another school's instructor.
        mockMvc.perform(post("/api/v1/lesson-questions")
                        .header("Authorization", bearer(studentA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subject", "Roundabouts",
                                "questionBody", "Who has priority on a mini roundabout?",
                                "assignedInstructorId", instructorB.profileId()))))
                .andExpect(status().isBadRequest());

        // An unassigned question is only open to instructors of the student's own school.
        MvcResult questionResult = mockMvc.perform(post("/api/v1/lesson-questions")
                        .header("Authorization", bearer(studentB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subject", "Mirrors",
                                "questionBody", "How often should I check my mirrors on a motorway?"))))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> question = (Map<String, Object>) objectMapper
                .readValue(questionResult.getResponse().getContentAsString(), Map.class).get("data");
        Object questionId = question.get("id");

        mockMvc.perform(post("/api/v1/lesson-questions/" + questionId + "/respond")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("response", "Every five to eight seconds."))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/lesson-questions/" + questionId + "/respond")
                        .header("Authorization", bearer(instructorB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("response", "Every five to eight seconds."))))
                .andExpect(status().isOk());

        // Published course content is confined to its own school too.
        MvcResult courseResult = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", bearer(instructorA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "School A Road Signs"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = parse(courseResult, CourseResponse.class).getId();
        mockMvc.perform(put("/api/v1/courses/" + courseId + "/publish").header("Authorization", bearer(instructorA.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId).header("Authorization", bearer(studentA.token())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/" + courseId).header("Authorization", bearer(studentB.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/quizzes/course/" + courseId).header("Authorization", bearer(studentB.token())))
                .andExpect(status().isBadRequest());

        MvcResult studentACourses = mockMvc.perform(get("/api/v1/courses").header("Authorization", bearer(studentA.token())))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult studentBCourses = mockMvc.perform(get("/api/v1/courses").header("Authorization", bearer(studentB.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(studentACourses.getResponse().getContentAsString()).contains("School A Road Signs");
        assertThat(studentBCourses.getResponse().getContentAsString()).doesNotContain("School A Road Signs");
    }
}
