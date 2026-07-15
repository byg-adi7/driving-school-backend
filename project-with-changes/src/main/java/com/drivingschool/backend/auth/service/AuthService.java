package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.AdminRegisterRequest;
import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.auth.dto.ForgotPasswordRequest;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.auth.dto.ResetPasswordRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse registerByAdmin(AdminRegisterRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    CurrentUserResponse getCurrentUser();

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void deleteCurrentAccount();
}
