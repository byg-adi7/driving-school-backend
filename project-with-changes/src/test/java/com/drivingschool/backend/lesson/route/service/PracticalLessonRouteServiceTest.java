package com.drivingschool.backend.lesson.route.service;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.lesson.route.dto.GenerateRouteRequest;
import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
import com.drivingschool.backend.lesson.route.repository.PracticalLessonRouteRepository;
import com.drivingschool.backend.lesson.route.validator.RouteValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticalLessonRouteServiceTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    @Mock private PracticalLessonRouteRepository routeRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private OpenRouteServiceIntegration openRouteService;
    private final RouteValidator validator = new RouteValidator(adminSchoolScope);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PracticalLessonRouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new PracticalLessonRouteService(routeRepository, userRepository,
                instructorProfileRepository, bookingRepository, openRouteService, validator, objectMapper);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InstructorProfile instructorProfile(Long profileId, User user) {
        InstructorProfile instructor = InstructorProfile.builder().user(user).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    private Booking bookingFor(InstructorProfile instructor, Long bookingId) {
        StudentProfile student = StudentProfile.builder().school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(student, "id", 60L);
        Booking booking = Booking.builder()
                .student(student)
                .instructor(instructor)
                .school(School.builder().active(true).build())
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .status(BookingStatus.CONFIRMED)
                .bookingType(BookingType.ROAD_LESSON)
                .build();
        ReflectionTestUtils.setField(booking, "id", bookingId);
        return booking;
    }

    private GenerateRouteRequest generateRequest(Long bookingId) {
        GenerateRouteRequest request = new GenerateRouteRequest();
        request.setBookingId(bookingId);
        request.setStartLocation("123 Main St");
        request.setDestinationLocation("456 Oak Ave");
        request.setStartLatitude(51.5);
        request.setStartLongitude(-0.1);
        request.setDestinationLatitude(51.6);
        request.setDestinationLongitude(-0.2);
        return request;
    }

    // --- generateRoute ---

    @Test
    void generateRoute_forUnknownBooking_throwsResourceNotFoundException() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(bookingRepository.findById(500L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.generateRoute(generateRequest(500L), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateRoute_forBookingBelongingToDifferentInstructor_throwsBadRequestException() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        InstructorProfile otherInstructor = instructorProfile(51L, userWithId(9L));
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(bookingFor(otherInstructor, 500L)));

        assertThatThrownBy(() -> routeService.generateRoute(generateRequest(500L), 1L))
                .isInstanceOf(BadRequestException.class);

        verify(routeRepository, never()).save(any());
    }

    @Test
    void getInstructorRoutes_asSelf_returnsRoutes() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.of(instructor));
        when(routeRepository.findByInstructorId(50L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        assertThatCode(() -> routeService.getInstructorRoutes(50L, Pageable.unpaged(), 1L, "INSTRUCTOR"))
                .doesNotThrowAnyException();
    }

    @Test
    void getInstructorRoutes_asDifferentInstructor_isDenied() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.of(instructor));

        assertThatThrownBy(() -> routeService.getInstructorRoutes(50L, Pageable.unpaged(), 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(routeRepository, never()).findByInstructorId(any(), any());
    }

    @Test
    void getInstructorRoutes_asAdmin_isAllowedRegardlessOfIdentity() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.of(instructor));
        when(routeRepository.findByInstructorId(50L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        assertThatCode(() -> routeService.getInstructorRoutes(50L, Pageable.unpaged(), 999L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void getInstructorRoutes_unknownInstructor_throwsResourceNotFoundException() {
        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.getInstructorRoutes(50L, Pageable.unpaged(), 1L, "ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllRoutes_asRegularAdmin_isFilteredToTheirSchool() {
        when(adminSchoolScope.restrictedSchoolId()).thenReturn(Optional.of(7L));
        when(routeRepository.findAllRoutesBySchoolId(7L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        routeService.getAllRoutes(Pageable.unpaged());

        verify(routeRepository).findAllRoutesBySchoolId(7L, Pageable.unpaged());
        verify(routeRepository, never()).findAllRoutes(any());
    }

    @Test
    void getAllRoutes_asBootstrapAdmin_isUnfiltered() {
        when(adminSchoolScope.restrictedSchoolId()).thenReturn(Optional.empty());
        when(routeRepository.findAllRoutes(Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        routeService.getAllRoutes(Pageable.unpaged());

        verify(routeRepository, never()).findAllRoutesBySchoolId(any(), any());
    }
}
