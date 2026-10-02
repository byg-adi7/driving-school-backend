package com.drivingschool.backend.school.mapper;

import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.entity.School;
import org.springframework.stereotype.Component;

@Component
public class SchoolMapper {

    public SchoolResponse toResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .name(school.getName())
                .address(school.getAddress())
                .phone(school.getPhone())
                .email(school.getEmail())
                .active(school.isActive())
                .latitude(school.getLatitude())
                .longitude(school.getLongitude())
                .attendanceRadiusMeters(school.getAttendanceRadiusMeters())
                .timeZone(school.getTimeZone())
                .createdAt(school.getCreatedAt())
                .updatedAt(school.getUpdatedAt())
                .build();
    }
}
