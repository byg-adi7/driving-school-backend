package com.drivingschool.backend.common.exception;

import java.util.Map;

/** A 400 whose response also carries data the client can show, e.g. how far away a check-in was. */
public class DetailedBadRequestException extends BadRequestException {

    private final transient Map<String, Object> details;

    public DetailedBadRequestException(String message, Map<String, Object> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
