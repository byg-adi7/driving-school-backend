package com.drivingschool.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * RBAC Annotation: Any authenticated user can access this endpoint/method
 * 
 * Usage:
 * @RequireAuthenticated
 * public ResponseEntity<ApiResponse<...>> getCurrentUser(...) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface RequireAuthenticated {
}
