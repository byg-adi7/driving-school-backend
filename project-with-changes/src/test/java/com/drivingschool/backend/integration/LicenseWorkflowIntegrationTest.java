package com.drivingschool.backend.integration;

import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;
import com.drivingschool.backend.progress.enums.LicenseStage;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full student license-progression workflow end to end through
 * real HTTP calls against a real Postgres + Redis stack: initialization,
 * ownership-gated theory-progress tracking with the auto-advance-at-100% rule,
 * the admin-only (not even instructor) quiz-passed gate, automated sequential
 * stage validation, and the instructor-controlled final stages which require
 * the caller to actually resolve to a real InstructorProfile - an ADMIN caller,
 * despite passing the role check, is rejected there for lacking one.
 */
class LicenseWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void studentProgressesThroughFullLicenseWorkflow() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "License Workflow Driving School");

        Person instructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "instructor.progress@example.com", "LIC-PROGRESS-1");
        Person student = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "student.progress@example.com", null);
        Person otherStudent = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "other.student.progress@example.com", null);

        // initialize
        MvcResult initResult = mockMvc.perform(post("/api/v1/progress/license/students/" + student.profileId() + "/initialize")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn();
        LicenseWorkflowResponse initialized = parse(initResult, LicenseWorkflowResponse.class);
        assertThat(initialized.getCurrentStage()).isEqualTo(LicenseStage.THEORY_LEARNING);
        assertThat(initialized.getTheoryProgressPercent()).isZero();

        // can't initialize twice
        mockMvc.perform(post("/api/v1/progress/license/students/" + student.profileId() + "/initialize")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        // the owning student can view their own workflow
        mockMvc.perform(get("/api/v1/progress/license/students/" + student.profileId())
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk());

        // an unrelated student cannot view someone else's workflow
        mockMvc.perform(get("/api/v1/progress/license/students/" + student.profileId())
                        .header("Authorization", bearer(otherStudent.token())))
                .andExpect(status().isBadRequest());

        // an instructor can never update theory progress - role-gated entirely, not just ownership
        mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/theory-progress")
                        .header("Authorization", bearer(instructor.token()))
                        .param("progressPercent", "50"))
                .andExpect(status().isForbidden());

        // an unrelated student cannot update this student's theory progress
        mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/theory-progress")
                        .header("Authorization", bearer(otherStudent.token()))
                        .param("progressPercent", "50"))
                .andExpect(status().isBadRequest());

        // the student updates their own progress partway - stage doesn't move yet
        MvcResult partialResult = mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/theory-progress")
                        .header("Authorization", bearer(student.token()))
                        .param("progressPercent", "50"))
                .andExpect(status().isOk())
                .andReturn();
        LicenseWorkflowResponse partial = parse(partialResult, LicenseWorkflowResponse.class);
        assertThat(partial.getTheoryProgressPercent()).isEqualTo(50);
        assertThat(partial.getCurrentStage()).isEqualTo(LicenseStage.THEORY_LEARNING);

        // reaching 100% auto-advances the stage
        MvcResult completeResult = mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/theory-progress")
                        .header("Authorization", bearer(student.token()))
                        .param("progressPercent", "100"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parse(completeResult, LicenseWorkflowResponse.class).getCurrentStage())
                .isEqualTo(LicenseStage.THEORY_COMPLETED);

        // marking quiz passed is admin-only - not even the instructor may call it directly
        mockMvc.perform(post("/api/v1/progress/license/students/" + student.profileId() + "/quiz-passed")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isForbidden());

        MvcResult quizPassedResult = mockMvc.perform(post("/api/v1/progress/license/students/" + student.profileId() + "/quiz-passed")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parse(quizPassedResult, LicenseWorkflowResponse.class).getCurrentStage())
                .isEqualTo(LicenseStage.QUIZ_PASSED);

        // instructor advances through the automated (non-instructor-controlled) stages sequentially
        advanceStage(instructor.token(), student.profileId(), LicenseStage.ROAD_TRAINING_STARTED, LicenseStage.ROAD_TRAINING_STARTED);
        advanceStage(instructor.token(), student.profileId(), LicenseStage.ROAD_TRAINING_IN_PROGRESS, LicenseStage.ROAD_TRAINING_IN_PROGRESS);

        // an ADMIN passes the role check on /advance but is rejected on an instructor-controlled
        // stage for having no real InstructorProfile to approve it with
        mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/advance")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AdvanceStageRequest.builder().targetStage(LicenseStage.ROAD_READY).build())))
                .andExpect(status().isBadRequest());

        // the instructor can, and gets recorded as the approver
        MvcResult roadReadyResult = mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/advance")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AdvanceStageRequest.builder().targetStage(LicenseStage.ROAD_READY).build())))
                .andExpect(status().isOk())
                .andReturn();
        LicenseWorkflowResponse roadReady = parse(roadReadyResult, LicenseWorkflowResponse.class);
        assertThat(roadReady.getCurrentStage()).isEqualTo(LicenseStage.ROAD_READY);
        assertThat(roadReady.getApprovedByInstructorId()).isEqualTo(instructor.profileId());

        advanceStage(instructor.token(), student.profileId(), LicenseStage.DVLA_PROCESSING, LicenseStage.DVLA_PROCESSING);
        advanceStage(instructor.token(), student.profileId(), LicenseStage.LICENSE_APPROVED, LicenseStage.LICENSE_APPROVED);

        // once fully approved, an invalid backward "advance" is rejected
        mockMvc.perform(put("/api/v1/progress/license/students/" + student.profileId() + "/advance")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AdvanceStageRequest.builder().targetStage(LicenseStage.QUIZ_PASSED).build())))
                .andExpect(status().isBadRequest());
    }

    private void advanceStage(String callerToken, Long studentProfileId, LicenseStage target, LicenseStage expected) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/progress/license/students/" + studentProfileId + "/advance")
                        .header("Authorization", bearer(callerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AdvanceStageRequest.builder().targetStage(target).build())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parse(result, LicenseWorkflowResponse.class).getCurrentStage()).isEqualTo(expected);
    }
}
