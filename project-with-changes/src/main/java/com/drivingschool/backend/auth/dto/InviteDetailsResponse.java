package com.drivingschool.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** What the accept-invite page shows before the person types anything. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteDetailsResponse {

    private final String firstName;
    private final String email;
    private final String schoolName;
    private final String role;
    private final LocalDateTime expiresAt;
}
