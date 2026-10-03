package com.drivingschool.backend.school.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class SchoolWithAdminResponse {

    private final SchoolResponse school;
    private final Long adminUserId;
    private final String adminEmail;
    // Only when the admin was created without a password.
    private final com.drivingschool.backend.auth.dto.InviteResponse invite;
}
