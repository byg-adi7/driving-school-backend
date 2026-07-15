package com.drivingschool.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * RBAC Annotation: Only INSTRUCTOR users can access this endpoint/method
 * 
 * Usage:
 * @RequireInstructor
 * public ResponseEntity<ApiResponse<...>> scheduleLesson(...) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('INSTRUCTOR')")
public @interface RequireInstructor {
}
