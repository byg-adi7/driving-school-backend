package com.drivingschool.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * The invite sent for a new account. url is only ever returned to the person who
 * created or resent it (to share by e.g. WhatsApp while email can't reach everyone) -
 * never in lists.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteResponse {

    /** SENT (email accepted by the mail service) or FAILED (use the url, or Resend). */
    private final String status;
    private final LocalDateTime expiresAt;
    private final String url;
}
