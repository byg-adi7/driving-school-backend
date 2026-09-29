package com.drivingschool.backend.messaging.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * The OTHER participant, by profile id: a student passes the instructor's
 * InstructorProfile.id, an instructor passes the student's StudentProfile.id.
 */
@Getter
@Builder
@Jacksonized
public class StartConversationRequest {

    @NotNull(message = "The other participant's profile ID is required")
    private final Long participantProfileId;
}
