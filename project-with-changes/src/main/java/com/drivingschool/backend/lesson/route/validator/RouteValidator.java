package com.drivingschool.backend.lesson.route.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.route.dto.GenerateRouteRequest;
import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
import org.springframework.stereotype.Component;

@Component
public class RouteValidator {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LON = -180.0;
    private static final double MAX_LON = 180.0;

    public void validateGenerateRequest(GenerateRouteRequest request) {
        validateCoordinates(
                request.getStartLatitude(), request.getStartLongitude(),
                "Start location"
        );

        validateCoordinates(
                request.getDestinationLatitude(), request.getDestinationLongitude(),
                "Destination location"
        );

        if (Math.abs(request.getStartLatitude() - request.getDestinationLatitude()) < 0.0001 &&
                Math.abs(request.getStartLongitude() - request.getDestinationLongitude()) < 0.0001) {
            throw new BadRequestException("Start and destination locations cannot be the same");
        }
    }

    private void validateCoordinates(Double latitude, Double longitude, String locationName) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException(locationName + " coordinates are required");
        }

        if (latitude < MIN_LAT || latitude > MAX_LAT) {
            throw new BadRequestException(locationName + " latitude must be between -90 and 90");
        }

        if (longitude < MIN_LON || longitude > MAX_LON) {
            throw new BadRequestException(locationName + " longitude must be between -180 and 180");
        }
    }

    public void validateReadAccess(PracticalLessonRoute route, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && route.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this route");
    }

    public void validateOwnership(PracticalLessonRoute route, Long callerId) {
        if (!route.getInstructor().getUser().getId().equals(callerId)) {
            throw new BadRequestException("You are not authorized to delete this route");
        }
    }

    public void validateInstructorRoutesAccess(InstructorProfile instructor, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && instructor.getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this instructor's routes");
    }
}
