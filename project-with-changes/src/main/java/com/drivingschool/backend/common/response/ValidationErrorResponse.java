package com.drivingschool.backend.common.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ValidationErrorResponse {

    private final boolean success;
    private final String message;
    private final Map<String, List<String>> errors;
}
