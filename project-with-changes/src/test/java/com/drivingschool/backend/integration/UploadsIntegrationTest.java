package com.drivingschool.backend.integration;

import com.drivingschool.backend.lesson.note.dto.AttachmentResponse;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full lesson-note attachment (upload/list/download/replace/delete)
 * lifecycle end to end through real HTTP calls against a real Postgres + Redis
 * stack: ownership-gated upload, content-validated rejection (wrong extension and
 * spoofed PDF content), read-access-gated listing and download with a genuine
 * byte-for-byte round trip, replace resetting the download counter, and
 * uploader-scoped delete permission - closing out the "uploads" flow area that
 * BookingLifecycleIntegrationTest only touched in passing.
 */
class UploadsIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4\n%%EOF".getBytes();
    private static final byte[] REPLACEMENT_PDF_CONTENT = "%PDF-1.4\nreplacement\n%%EOF".getBytes();

    @Test
    void attachmentLifecycle_uploadListDownloadReplaceDelete() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Uploads Flow Driving School");

        Person instructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "instructor.uploads@example.com", "LIC-UPLOADS-1");
        Person otherInstructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "other.instructor.uploads@example.com", "LIC-UPLOADS-2");
        Person student = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "student.uploads@example.com", null);
        Person otherStudent = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "other.student.uploads@example.com", null);

        LessonNoteResponse note = createLessonNote(instructor, student);

        // student can't upload at all - role-gated
        mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "handbook.pdf", "application/pdf", VALID_PDF_CONTENT))
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isForbidden());

        // an unrelated instructor can't upload to someone else's lesson note
        mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "handbook.pdf", "application/pdf", VALID_PDF_CONTENT))
                        .header("Authorization", bearer(otherInstructor.token())))
                .andExpect(status().isBadRequest());

        // a wrong file extension is rejected regardless of a valid PDF Content-Type header
        mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "notes.txt", "application/pdf", VALID_PDF_CONTENT))
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isBadRequest());

        // content that doesn't actually match the claimed PDF type is rejected
        mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a real pdf".getBytes()))
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isBadRequest());

        // the owning instructor uploads a genuine PDF
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "handbook.pdf", "application/pdf", VALID_PDF_CONTENT))
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isCreated())
                .andReturn();
        AttachmentResponse attachment = parse(uploadResult, AttachmentResponse.class);
        assertThat(attachment.getFileName()).isEqualTo("handbook.pdf");
        assertThat(attachment.getDownloadCount()).isZero();
        Long attachmentId = attachment.getId();

        // the owning student can list it
        MvcResult listAsStudentResult = mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parseList(listAsStudentResult)).hasSize(1);

        // an unrelated student cannot list attachments for this note
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments")
                        .header("Authorization", bearer(otherStudent.token())))
                .andExpect(status().isBadRequest());

        // paginated listing works for the owning instructor too
        MvcResult paginatedResult = mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments/page")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parsePageContent(paginatedResult)).hasSize(1);

        // the owning student can download it, byte for byte
        MvcResult downloadResult = mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId()
                        + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(VALID_PDF_CONTENT);
        assertThat(downloadResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("handbook.pdf");

        // an unrelated student or instructor cannot download it
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(otherStudent.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(otherInstructor.token())))
                .andExpect(status().isBadRequest());

        // replacing resets the download counter and swaps the file
        MvcResult replaceResult = mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT,
                        "/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId)
                        .file(new MockMultipartFile("file", "handbook-v2.pdf", "application/pdf", REPLACEMENT_PDF_CONTENT))
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk())
                .andReturn();
        AttachmentResponse replaced = parse(replaceResult, AttachmentResponse.class);
        assertThat(replaced.getFileName()).isEqualTo("handbook-v2.pdf");
        assertThat(replaced.getDownloadCount()).isZero();

        MvcResult downloadReplacedResult = mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId()
                        + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloadReplacedResult.getResponse().getContentAsByteArray()).isEqualTo(REPLACEMENT_PDF_CONTENT);

        // only the uploader (or ADMIN) may delete - not an unrelated instructor
        mockMvc.perform(delete("/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", bearer(otherInstructor.token())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk());

        // it's really gone
        mockMvc.perform(get("/api/v1/lesson-notes/" + note.getId() + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isNotFound());
    }

    private LessonNoteResponse createLessonNote(Person instructor, Person student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lesson-notes")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", student.profileId(),
                                "lessonSummary", "A solid first lesson on quiet residential roads.",
                                "strengths", "Good mirror checks and steady steering control throughout.",
                                "weaknesses", "Needs to slow down earlier before junctions and roundabouts.",
                                "recommendations", "Practice roundabouts and give-way rules next session."))))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result, LessonNoteResponse.class);
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseList(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (List<Object>) body.get("data");
    }

    /** For endpoints returning a paginated Page<T> - "data" is the page object, not a bare array. */
    @SuppressWarnings("unchecked")
    private List<Object> parsePageContent(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> page = (Map<String, Object>) body.get("data");
        return (List<Object>) page.get("content");
    }
}
