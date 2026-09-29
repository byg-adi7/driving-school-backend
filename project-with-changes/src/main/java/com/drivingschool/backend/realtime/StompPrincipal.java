package com.drivingschool.backend.realtime;

import java.security.Principal;

/**
 * The authenticated user of a STOMP session. Its name is the User.id, which is what
 * RealtimePublisher addresses: Spring resolves /user/queue/events to the sessions
 * whose principal has that name.
 */
public record StompPrincipal(String name) implements Principal {

    public static StompPrincipal forUser(Long userId) {
        return new StompPrincipal(String.valueOf(userId));
    }

    @Override
    public String getName() {
        return name;
    }
}
