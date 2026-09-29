package com.drivingschool.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
// Token fields are absent (not null) in a register response - see AuthMapper.toRegisteredUserResponse -
// and in a login response for an unverified account; the verification fields only appear then.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final Long expiresIn;
    private final UserInfo user;

    // Set (with no tokens) when the password was right but the account still has to be
    // verified with a one-time code - see POST /auth/verification/send and /confirm.
    private final Boolean verificationRequired;
    private final VerificationChallengeResponse verification;

    @Getter
    @Builder
    public static class UserInfo {
        private final Long id;
        private final String email;
        private final Set<String> roles;
    }
}
