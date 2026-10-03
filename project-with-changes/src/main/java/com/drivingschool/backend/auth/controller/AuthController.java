package com.drivingschool.backend.auth.controller;

import com.drivingschool.backend.auth.dto.AdminRegisterRequest;
import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.ConfirmVerificationRequest;
import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.auth.dto.ForgotPasswordRequest;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.auth.dto.ResetPasswordRequest;
import com.drivingschool.backend.auth.dto.SendVerificationCodeRequest;
import com.drivingschool.backend.auth.service.AuthService;
import com.drivingschool.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and registration endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @GetMapping("/invite/{token}")
    @Operation(summary = "Who an invite link is for - shown before they choose a password")
    public ResponseEntity<ApiResponse<com.drivingschool.backend.auth.dto.InviteDetailsResponse>> invite(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(authService.getInvite(token)));
    }

    @PostMapping("/invite/accept")
    @Operation(summary = "Choose a password from an invite link - signs the person in")
    public ResponseEntity<ApiResponse<AuthResponse>> acceptInvite(
            @Valid @RequestBody com.drivingschool.backend.auth.dto.AcceptInviteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Account set up", authService.acceptInvite(request)));
    }

    @PostMapping("/verification/send")
    @Operation(summary = "Send a one-time verification code for an unverified account",
            description = "Uses the challengeId from a login response with verificationRequired=true. " +
                    "Channel EMAIL or WHATSAPP (only when listed in that response's channels). " +
                    "A new code replaces the previous one; one send per 60 seconds (429 otherwise).")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        authService.sendVerificationCode(request);
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", null));
    }

    @PostMapping("/verification/confirm")
    @Operation(summary = "Confirm the one-time code: verifies the account and logs in",
            description = "Codes are 6 digits, valid for 10 minutes, 5 attempts each. " +
                    "Returns the same tokens as a normal login.")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmVerification(@Valid @RequestBody ConfirmVerificationRequest request) {
        AuthResponse response = authService.confirmVerification(request);
        return ResponseEntity.ok(ApiResponse.success("Account verified", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new student or instructor account",
            description = "No public self-registration: instructors can only be created by an admin " +
                    "(see /admin/register); students can be created by an admin or by an instructor " +
                    "(who may only create students in their own school).")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser()));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If an account with that email exists, a password reset link has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a valid reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully.", null));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete (soft-delete) the current authenticated user's account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteCurrentAccount() {
        authService.deleteCurrentAccount();
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/admin/register")
    @Operation(summary = "Admin creates a user with any role including ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse>> registerByAdmin(
            @Valid @RequestBody AdminRegisterRequest request) {
        AuthResponse response = authService.registerByAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }
}
