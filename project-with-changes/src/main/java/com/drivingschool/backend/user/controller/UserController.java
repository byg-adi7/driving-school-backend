package com.drivingschool.backend.user.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Admin user account management")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Admin: delete a user account",
            description = "Soft-deletes STUDENT/INSTRUCTOR targets. For a non-bootstrap ADMIN target, only the " +
                    "bootstrap admin may call this, and it cascade-deletes the target's school along with it. " +
                    "The bootstrap admin itself can never be targeted.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        Long callerId = SecurityUtils.getCurrentUserId();
        userService.deleteUserAccount(id, callerId);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }
}
