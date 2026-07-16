package com.drivingschool.backend.vehicle.controller;

import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.drivingschool.backend.vehicle.dto.CreateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleStatusRequest;
import com.drivingschool.backend.vehicle.dto.VehicleResponse;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import com.drivingschool.backend.vehicle.service.VehicleService;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = VehicleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(VehicleControllerSecurityTest.MethodSecurityTestConfig.class)
class VehicleControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private VehicleService vehicleService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateVehicleRequest createRequest() {
        return CreateVehicleRequest.builder()
                .registrationNumber("ABC123").make("Toyota").model("Corolla")
                .modelYear(2022).color("White").schoolId(1L).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_isCreated() throws Exception {
        when(vehicleService.create(any())).thenReturn(VehicleResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/vehicles").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getById_asStudent_isOk() throws Exception {
        when(vehicleService.getById(1L)).thenReturn(VehicleResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/vehicles/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getBySchool_asStudent_isOk() throws Exception {
        when(vehicleService.getBySchool(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/vehicles/school/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void update_asInstructor_isForbidden() throws Exception {
        UpdateVehicleRequest request = UpdateVehicleRequest.builder()
                .make("Honda").model("Civic").modelYear(2023).color("Black").build();

        mockMvc.perform(put("/api/v1/vehicles/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_isOk() throws Exception {
        when(vehicleService.update(anyLong(), any())).thenReturn(VehicleResponse.builder().id(1L).build());
        UpdateVehicleRequest request = UpdateVehicleRequest.builder()
                .make("Honda").model("Civic").modelYear(2023).color("Black").build();

        mockMvc.perform(put("/api/v1/vehicles/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void updateStatus_asInstructor_isForbidden() throws Exception {
        UpdateVehicleStatusRequest request = UpdateVehicleStatusRequest.builder()
                .status(VehicleStatus.MAINTENANCE).build();

        mockMvc.perform(patch("/api/v1/vehicles/1/status").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_asAdmin_isOk() throws Exception {
        when(vehicleService.updateStatus(anyLong(), any())).thenReturn(VehicleResponse.builder().id(1L).build());
        UpdateVehicleStatusRequest request = UpdateVehicleStatusRequest.builder()
                .status(VehicleStatus.MAINTENANCE).build();

        mockMvc.perform(patch("/api/v1/vehicles/1/status").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
