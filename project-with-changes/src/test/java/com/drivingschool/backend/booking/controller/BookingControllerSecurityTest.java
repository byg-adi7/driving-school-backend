package com.drivingschool.backend.booking.controller;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.dto.CreateBookingRequest;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.booking.security.BookingSecurity;
import com.drivingschool.backend.booking.service.BookingService;
import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the IDOR fix in {@link BookingController} is actually enforced at the HTTP
 * layer, not just correct in isolation: {@link BookingSecurity} is mocked so these
 * tests exercise exactly what the {@code @PreAuthorize} SpEL expressions decide,
 * independent of the real ownership-lookup logic (covered separately in
 * BookingSecurityTest).
 */
@WebMvcTest(
        controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(BookingControllerSecurityTest.MethodSecurityTestConfig.class)
class BookingControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private BookingService bookingService;
    @MockBean(name = "bookingSecurity") private BookingSecurity bookingSecurity;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @org.springframework.context.annotation.Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper;
        }
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getById_asUnrelatedStudent_isForbidden() throws Exception {
        when(bookingSecurity.isParticipant(1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/bookings/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getById_asParticipantStudent_isOk() throws Exception {
        when(bookingSecurity.isParticipant(1L)).thenReturn(true);
        when(bookingService.getById(1L)).thenReturn(BookingResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/bookings/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_asAdmin_bypassesOwnershipCheck() throws Exception {
        when(bookingService.getById(1L)).thenReturn(BookingResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/bookings/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void cancel_asUnrelatedStudent_isForbidden() throws Exception {
        when(bookingSecurity.isParticipant(1L)).thenReturn(false);

        mockMvc.perform(put("/api/v1/bookings/1/cancel").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void confirm_asUnassignedInstructor_isForbidden() throws Exception {
        when(bookingSecurity.isAssignedInstructor(1L)).thenReturn(false);

        mockMvc.perform(put("/api/v1/bookings/1/confirm").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void confirm_asAssignedInstructor_isOk() throws Exception {
        when(bookingSecurity.isAssignedInstructor(1L)).thenReturn(true);
        when(bookingService.confirm(1L)).thenReturn(BookingResponse.builder().id(1L).build());

        mockMvc.perform(put("/api/v1/bookings/1/confirm").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getByStudent_asDifferentStudent_isForbidden() throws Exception {
        when(bookingSecurity.isSelfStudent(5L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/bookings/student/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getByStudent_asInstructorWhoNeverTaughtThem_isForbidden() throws Exception {
        when(bookingSecurity.hasTaughtStudent(5L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/bookings/student/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_bookingForAnotherStudent_isForbidden() throws Exception {
        when(bookingSecurity.isSelfStudent(99L)).thenReturn(false);
        CreateBookingRequest request = CreateBookingRequest.builder()
                .studentId(99L)
                .instructorId(2L)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .bookingType(BookingType.ROAD_LESSON)
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
