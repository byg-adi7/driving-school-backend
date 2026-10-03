package com.drivingschool.backend.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class CurrentUserResponse {

    private final Long userId;
    private final String email;
    private final Set<String> roles;
    private final Long studentProfileId;
    private final Long instructorProfileId;
    private final Long schoolId;
    private final String profileImageUrl;
    private final boolean enabled;
    private final boolean emailVerified;
    private final boolean bootstrapAdmin;
    // INVITED until the person sets their password; inviteExpiresAt only while INVITED.
    private final String accountStatus;
    private final java.time.LocalDateTime inviteExpiresAt;
}
