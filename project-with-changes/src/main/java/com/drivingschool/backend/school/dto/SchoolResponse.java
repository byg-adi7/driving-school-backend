package com.drivingschool.backend.school.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SchoolResponse {

    private final Long id;
    private final String name;
    private final String address;
    private final String phone;
    private final String email;
    private final boolean active;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
