package com.drivingschool.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * RBAC Annotation: Only STUDENT users can access this endpoint/method
 * 
 * Usage:
 * @RequireStudent
 * public ResponseEntity<ApiResponse<...>> bookLesson(...) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('STUDENT')")
public @interface RequireStudent {
}
