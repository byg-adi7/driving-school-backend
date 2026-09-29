package com.drivingschool.backend.messaging.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Someone the caller can start a conversation with: a student sees their school's
 * active instructors, an instructor sees their school's students. profileId is what
 * POST /conversations takes.
 */
@Getter
@Builder
public class ContactResponse {

    private final Long profileId;
    private final String firstName;
    private final String lastName;
}
