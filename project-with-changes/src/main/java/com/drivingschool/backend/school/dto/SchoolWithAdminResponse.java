package com.drivingschool.backend.school.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolWithAdminResponse {

    private final SchoolResponse school;
    private final Long adminUserId;
    private final String adminEmail;
}
