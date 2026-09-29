package com.drivingschool.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
// Token fields are absent (not null) in a register response - see AuthMapper.toRegisteredUserResponse.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final Long expiresIn;
    private final UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private final Long id;
        private final String email;
        private final Set<String> roles;
    }
}
