package com.drivingschool.backend.lesson.route.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.route.dto.GenerateRouteRequest;
import com.drivingschool.backend.lesson.route.dto.RouteCoordinateDTO;
import com.drivingschool.backend.lesson.route.dto.RouteResponse;
import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
import com.drivingschool.backend.lesson.route.repository.PracticalLessonRouteRepository;
import com.drivingschool.backend.lesson.route.validator.RouteValidator;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PracticalLessonRouteService {

    private final PracticalLessonRouteRepository routeRepository;
    private final UserRepository userRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final OpenRouteServiceIntegration openRouteService;
    private final RouteValidator validator;
    private final ObjectMapper objectMapper;

    @Transactional
    public RouteResponse generateRoute(GenerateRouteRequest request, Long instructorId) {
        validator.validateGenerateRequest(request);

        var instructor = instructorProfileRepository.findByUserId(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + instructorId));

        Map<String, Object> routeData = openRouteService.generateRoute(
                request.getStartLatitude(), request.getStartLongitude(),
                request.getDestinationLatitude(), request.getDestinationLongitude()
        );

        PracticalLessonRoute route = PracticalLessonRoute.builder()
                .liveSessionId(request.getLiveSessionId())
                .instructor(instructor)
                .startLocation(request.getStartLocation())
                .destinationLocation(request.getDestinationLocation())
                .startLatitude(request.getStartLatitude())
                .startLongitude(request.getStartLongitude())
                .destinationLatitude(request.getDestinationLatitude())
                .destinationLongitude(request.getDestinationLongitude())
                .distanceMeters((Long) routeData.get("distance"))
                .durationSeconds((Long) routeData.get("duration"))
                .routeGeometry((String) routeData.get("geometry"))
                .externalRouteId((String) routeData.get("externalRouteId"))
                .build();

        PracticalLessonRoute savedRoute = routeRepository.save(route);
        return mapToResponse(savedRoute, (List<RouteCoordinateDTO>) routeData.get("coordinates"));
    }

    @Transactional(readOnly = true)
    public RouteResponse getRoute(Long routeId, Long userId, String role) {
        PracticalLessonRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + routeId));

        validator.validateReadAccess(route, userId, role);

        List<RouteCoordinateDTO> coordinates = parseRouteGeometry(route.getRouteGeometry());
        return mapToResponse(route, coordinates);
    }

    @Transactional(readOnly = true)
    public RouteResponse getRouteByLiveSession(Long liveSessionId, Long userId, String role) {
        PracticalLessonRoute route = routeRepository.findByLiveSessionId(liveSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found for live session: " + liveSessionId));

        validator.validateReadAccess(route, userId, role);

        List<RouteCoordinateDTO> coordinates = parseRouteGeometry(route.getRouteGeometry());
        return mapToResponse(route, coordinates);
    }

    @Transactional(readOnly = true)
    public Page<RouteResponse> getInstructorRoutes(Long instructorId, Pageable pageable, Long userId, String role) {
        InstructorProfile instructor = instructorProfileRepository.findByUserId(instructorId)
                .or(() -> instructorProfileRepository.findById(instructorId))
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found: " + instructorId));
        validator.validateInstructorRoutesAccess(instructor, userId, role);

        Page<PracticalLessonRoute> routes = routeRepository.findByInstructorId(instructor.getId(), pageable);
        return routes.map(route -> {
            List<RouteCoordinateDTO> coordinates = parseRouteGeometry(route.getRouteGeometry());
            return mapToResponse(route, coordinates);
        });
    }

    @Transactional(readOnly = true)
    public Page<RouteResponse> getAllRoutes(Pageable pageable) {
        Page<PracticalLessonRoute> routes = routeRepository.findAllRoutes(pageable);
        return routes.map(route -> {
            List<RouteCoordinateDTO> coordinates = parseRouteGeometry(route.getRouteGeometry());
            return mapToResponse(route, coordinates);
        });
    }

    @Transactional
    public void deleteRoute(Long routeId, Long instructorId) {
        PracticalLessonRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + routeId));

        validator.validateOwnership(route, instructorId);
        routeRepository.delete(route);
    }

    private List<RouteCoordinateDTO> parseRouteGeometry(String geometry) {
        try {
            return objectMapper.readValue(geometry, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RouteCoordinateDTO.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private RouteResponse mapToResponse(PracticalLessonRoute route, List<RouteCoordinateDTO> coordinates) {
        return RouteResponse.builder()
                .id(route.getId())
                .liveSessionId(route.getLiveSessionId())
                                .instructorId(route.getInstructor().getUser().getId())
                                .instructorName(route.getInstructor().getUser().getDisplayName())
                .startLocation(route.getStartLocation())
                .destinationLocation(route.getDestinationLocation())
                .startLatitude(route.getStartLatitude())
                .startLongitude(route.getStartLongitude())
                .destinationLatitude(route.getDestinationLatitude())
                .destinationLongitude(route.getDestinationLongitude())
                .distanceKm(route.getDistance())
                .durationMinutes(route.getDurationMinutes())
                .coordinates(coordinates)
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }
}
