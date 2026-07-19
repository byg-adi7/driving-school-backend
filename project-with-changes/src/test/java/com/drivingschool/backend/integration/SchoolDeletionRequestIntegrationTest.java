package com.drivingschool.backend.integration;

import com.drivingschool.backend.auth.dto.AdminRegisterRequest;
import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full school/admin ownership + approval-gated deletion lifecycle against a real
 * (Testcontainers-backed) Postgres + Redis stack, through the actual HTTP layer -
 * proves the V15 migration's cascade fixes, the bootstrap-admin gating, and the
 * request/notify/approve wiring all actually work together, not just each piece
 * in isolation (the unit tests for these classes all mock their collaborators).
 */
class SchoolDeletionRequestIntegrationTest extends AbstractIntegrationTest {

    private SchoolWithAdminResponse createSchoolWithAdmin(String bootstrapToken, String schoolName, String adminEmail) throws Exception {
        CreateSchoolWithAdminRequest request = CreateSchoolWithAdminRequest.builder()
                .schoolName(schoolName)
                .schoolAddress("1 Test Street")
                .adminEmail(adminEmail)
                .adminPassword("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", bearer(bootstrapToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return parse(result, SchoolWithAdminResponse.class);
    }

    @Test
    void fullLifecycle_adminRequestsDeletion_bootstrapApproves_schoolAndAdminAreGone() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse created = createSchoolWithAdmin(bootstrapToken, "Approve-Flow Driving School",
                "owner.approve@example.com");
        Long schoolId = created.getSchool().getId();
        String adminToken = login("owner.approve@example.com", "SecurePass123!");

        // Admin can see their own school, and only their own.
        mockMvc.perform(get("/api/v1/schools/" + schoolId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        // Request deletion of own school.
        MvcResult requestResult = mockMvc.perform(delete("/api/v1/schools/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isAccepted())
                .andReturn();
        SchoolDeletionRequestResponse deletionRequest = parse(requestResult, SchoolDeletionRequestResponse.class);
        assertThat(deletionRequest.getStatus()).isEqualTo(SchoolDeletionRequestStatus.PENDING);

        // A second request while one is already pending is rejected.
        mockMvc.perform(delete("/api/v1/schools/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        // Bootstrap sees it in the pending queue.
        MvcResult listResult = mockMvc.perform(get("/api/v1/school-deletion-requests").header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("owner.approve@example.com");

        // Bootstrap approves - school and admin account are both gone.
        mockMvc.perform(post("/api/v1/school-deletion-requests/" + deletionRequest.getId() + "/approve")
                        .header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/schools/" + schoolId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isNotFound());

        // The deleted admin's own (still-unexpired) JWT no longer authenticates -
        // their User row is hard-deleted, not just soft-deleted.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectFlow_leavesSchoolAndAdminFullyIntact() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse created = createSchoolWithAdmin(bootstrapToken, "Reject-Flow Driving School",
                "owner.reject@example.com");
        Long schoolId = created.getSchool().getId();
        String adminToken = login("owner.reject@example.com", "SecurePass123!");

        MvcResult requestResult = mockMvc.perform(delete("/api/v1/schools/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isAccepted())
                .andReturn();
        SchoolDeletionRequestResponse deletionRequest = parse(requestResult, SchoolDeletionRequestResponse.class);

        mockMvc.perform(post("/api/v1/school-deletion-requests/" + deletionRequest.getId() + "/reject")
                        .header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());

        // School still exists and the admin's token still works.
        mockMvc.perform(get("/api/v1/schools/" + schoolId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void bootstrapDirectDelete_cascadesImmediatelyWithoutARequest() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse created = createSchoolWithAdmin(bootstrapToken, "Direct-Delete Driving School",
                "owner.directdelete@example.com");
        Long schoolId = created.getSchool().getId();

        mockMvc.perform(delete("/api/v1/schools/" + schoolId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/schools/" + schoolId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bootstrapDeletingAdminAccount_cascadesTheirOwnedSchoolToo() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse created = createSchoolWithAdmin(bootstrapToken, "Delete-Via-User Driving School",
                "owner.viauser@example.com");
        Long schoolId = created.getSchool().getId();
        Long adminUserId = created.getAdminUserId();

        mockMvc.perform(delete("/api/v1/users/" + adminUserId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/schools/" + schoolId).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bootstrapAdmin_canNeverBeDeleted() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);

        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isOk())
                .andReturn();
        CurrentUserResponse me = parse(meResult, CurrentUserResponse.class);
        assertThat(me.isBootstrapAdmin()).isTrue();

        mockMvc.perform(delete("/api/v1/users/" + me.getUserId()).header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/auth/me").header("Authorization", bearer(bootstrapToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonBootstrapAdmin_cannotSeeOrActOnAnotherSchool() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse schoolA = createSchoolWithAdmin(bootstrapToken, "School A", "owner.a@example.com");
        SchoolWithAdminResponse schoolB = createSchoolWithAdmin(bootstrapToken, "School B", "owner.b@example.com");
        String adminAToken = login("owner.a@example.com", "SecurePass123!");

        mockMvc.perform(get("/api/v1/schools/" + schoolB.getSchool().getId()).header("Authorization", bearer(adminAToken)))
                .andExpect(status().isBadRequest());

        MvcResult listResult = mockMvc.perform(get("/api/v1/schools").header("Authorization", bearer(adminAToken)))
                .andExpect(status().isOk())
                .andReturn();
        String body = listResult.getResponse().getContentAsString();
        assertThat(body).contains("School A");
        assertThat(body).doesNotContain("School B");
    }

    @Test
    void nonBootstrapAdmin_cannotCreateSchoolOrOtherAdmins() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolWithAdminResponse created = createSchoolWithAdmin(bootstrapToken, "No-Escalation Driving School",
                "owner.noescalation@example.com");
        String adminToken = login("owner.noescalation@example.com", "SecurePass123!");

        CreateSchoolWithAdminRequest anotherSchool = CreateSchoolWithAdminRequest.builder()
                .schoolName("Sneaky School").schoolAddress("2 Test Street")
                .adminEmail("sneaky@example.com").adminPassword("SecurePass123!").build();
        mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anotherSchool)))
                .andExpect(status().isBadRequest());

        AdminRegisterRequest adminRegisterRequest = AdminRegisterRequest.builder()
                .email("second.admin@example.com").password("SecurePass123!")
                .firstName("Second").lastName("Admin")
                .schoolId(created.getSchool().getId()).role(RoleName.ADMIN).build();
        mockMvc.perform(post("/api/v1/auth/admin/register")
                        .header("Authorization", bearer(bootstrapToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRegisterRequest)))
                .andExpect(status().isBadRequest());
    }
}
