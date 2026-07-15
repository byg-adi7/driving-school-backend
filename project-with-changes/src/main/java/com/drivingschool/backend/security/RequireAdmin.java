package com.drivingschool.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * RBAC Annotation: Only ADMIN users can access this endpoint/method
 * 
 * Usage:
 * @RequireAdmin
 * public ResponseEntity<ApiResponse<...>> deleteUser(@PathVariable Long id) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface RequireAdmin {
}
