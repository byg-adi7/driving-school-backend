package com.drivingschool.backend.common.exception;

/**
 * HTTP 403: the caller is authenticated but not allowed to touch this record - it
 * belongs to someone else or to another school, or their role can't do this. Bad
 * input stays a {@link BadRequestException} (400), so a client can tell "you may not"
 * from "fix your request".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
