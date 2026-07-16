package com.drivingschool.backend.lesson.note.controller;

import com.drivingschool.backend.lesson.note.dto.AttachmentResponse;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.lesson.note.dto.UpdateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.service.LessonNoteAttachmentService;
import com.drivingschool.backend.lesson.note.service.LessonNoteService;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.SecurityTestUtils;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LessonNoteController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(LessonNoteControllerSecurityTest.MethodSecurityTestConfig.class)
class LessonNoteControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LessonNoteService lessonNoteService;
    @MockBean private LessonNoteAttachmentService attachmentService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateLessonNoteRequest validCreateRequest() {
        CreateLessonNoteRequest request = new CreateLessonNoteRequest();
        request.setStudentId(1L);
        request.setLessonSummary("A solid first lesson covering basic maneuvers.");
        request.setStrengths("Good mirror checks and steady acceleration.");
        request.setWeaknesses("Needs work on parallel parking.");
        request.setRecommendations("Practice parking in a quiet lot next session.");
        return request;
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createLessonNote_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/lesson-notes").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createLessonNote_asInstructor_isOk() throws Exception {
        when(lessonNoteService.createLessonNote(any(), anyLong()))
                .thenReturn(LessonNoteResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/lesson-notes")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateLessonNote_asStudent_isForbidden() throws Exception {
        UpdateLessonNoteRequest request = new UpdateLessonNoteRequest();
        request.setLessonSummary("Updated summary of at least ten characters.");

        mockMvc.perform(put("/api/v1/lesson-notes/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getInstructorNotes_asStudent_isForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/lesson-notes/instructor/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getAllNotes_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/lesson-notes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void deleteLessonNote_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/lesson-notes/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void uploadAttachment_asStudent_isForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/lesson-notes/1/attachments").file(file).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadAttachment_asInstructor_isOk() throws Exception {
        when(attachmentService.uploadAttachment(anyLong(), any(), any()))
                .thenReturn(AttachmentResponse.builder().id(1L).build());
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/lesson-notes/1/attachments").file(file)
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void deleteAttachment_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/lesson-notes/1/attachments/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void replaceAttachment_asStudent_isForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "content".getBytes());
        var request = multipart("/api/v1/lesson-notes/1/attachments/1").file(file).with(csrf());
        request.with(req -> {
            req.setMethod("PUT");
            return req;
        });

        mockMvc.perform(request).andExpect(status().isForbidden());
    }
}
