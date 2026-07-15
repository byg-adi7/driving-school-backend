package com.drivingschool.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * RBAC Annotation: ADMIN or INSTRUCTOR users can access this endpoint/method
 * 
 * Usage:
 * @RequireAdminOrInstructor
 * public ResponseEntity<ApiResponse<...>> viewBookings(...) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public @interface RequireAdminOrInstructor {
}
