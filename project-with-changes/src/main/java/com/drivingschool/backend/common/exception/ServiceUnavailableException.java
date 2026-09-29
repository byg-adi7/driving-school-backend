package com.drivingschool.backend.common.exception;

/** HTTP 503: an external provider the request depends on (email, WhatsApp) failed or isn't configured. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
