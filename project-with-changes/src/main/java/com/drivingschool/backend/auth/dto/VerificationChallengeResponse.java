package com.drivingschool.backend.auth.dto;

import com.drivingschool.backend.auth.enums.VerificationChannel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Returned by login for an account that hasn't been verified yet, instead of tokens.
 * The client picks one of {@code channels}, asks for a code with the challenge id, and
 * exchanges the code for tokens. Destinations are masked - the caller has proven the
 * password, but the response still shouldn't spell out a full phone number.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationChallengeResponse {

    private final String challengeId;
    private final long expiresInSeconds;
    private final List<VerificationChannel> channels;
    private final String maskedEmail;
    private final String maskedPhone;
}
